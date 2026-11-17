using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

class Launcher {
 sealed class LaunchMode {
  internal readonly string Name,Pointer,Session,Fallback,Title;
  internal LaunchMode(string name,string pointer,string session,string fallback,string title){Name=name;Pointer=pointer;Session=session;Fallback=fallback;Title=title;}
 }
 static LaunchMode Mode(string[] args) {
  string requested=null;
  foreach(string arg in args)if(arg.StartsWith("--mode=",StringComparison.OrdinalIgnoreCase))requested=arg.Substring(7).Trim().ToLowerInvariant();
  string executable=Path.GetFileNameWithoutExtension(Application.ExecutablePath).ToLowerInvariant();
  if(String.IsNullOrEmpty(requested))requested=executable.Contains("admin")?"admin":executable.Contains("slayer")?"slayer":executable.Contains("gauntlet")?"gauntlet":executable.Contains("toa")?"toa":"chambers";
  if(requested=="admin")return new LaunchMode("admin","release-admin.txt","runtime/companion-session-admin.txt","TurboAdmin.jar","Turbo Admin Tool startup");
  if(requested=="slayer")return new LaunchMode("slayer","release-slayer.txt","runtime/companion-session-slayer.txt","TurboSlayer.jar","Turbo Slayer startup");
  if(requested=="toa")return new LaunchMode("toa","release-toa-host.txt","runtime/companion-session-toa.txt","TurboTOAHost.jar","Turbo TOA startup");
  if(requested=="gauntlet")return new LaunchMode("gauntlet","release-gauntlet-host.txt","runtime/companion-session-gauntlet.txt","TurboGauntletHost.jar","Turbo Gauntlet startup");
  if(requested!="chambers")throw new Exception("Unknown Turbo feature mode: "+requested);
  return new LaunchMode("chambers","release-chambers.txt","runtime/companion-session-chambers.txt","TurboChambers.jar","Turbo Chambers startup");
 }
 static string Artifact(string root,string marker,string fallback) {
  string pointer=Path.Combine(root,marker);string relative=File.Exists(pointer)?File.ReadAllText(pointer).Trim():fallback;
  string resolved=Path.GetFullPath(Path.Combine(root,relative));string boundary=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar;
  if(!resolved.StartsWith(boundary,StringComparison.OrdinalIgnoreCase)||relative.IndexOf('"')>=0)throw new Exception("Invalid local release selection.");return resolved;
 }
 static string JavaRuntime(string root){string marker=Path.Combine(root,"java-runtime.txt");if(File.Exists(marker)){string selected=File.ReadAllText(marker).Trim();if(Path.IsPathRooted(selected)&&Path.GetFileName(selected).Equals("javaw.exe",StringComparison.OrdinalIgnoreCase)&&File.Exists(selected))return selected;}string preferred=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Orion","jre","bin","javaw.exe");if(File.Exists(preferred))return preferred;string home=Environment.GetEnvironmentVariable("JAVA_HOME");if(!String.IsNullOrEmpty(home)){string candidate=Path.Combine(home,"bin","javaw.exe");if(File.Exists(candidate))return candidate;}return preferred;}
 static string MutexName(string root,string mode) {using(var hash=SHA256.Create())return "Local\\OwlOrionCompanion-"+mode+"-"+BitConverter.ToString(hash.ComputeHash(Encoding.UTF8.GetBytes(Path.GetFullPath(root).ToUpperInvariant()))).Replace("-","").Substring(0,24);}
 static bool Reload(string root,LaunchMode mode) {
  string file=Path.Combine(root,mode.Session.Replace('/',Path.DirectorySeparatorChar));
  // Migration only: an Orion process opened by the old Turbo Client publishes the
  // legacy Chambers endpoint until it is restarted once with the separated build.
  if(!File.Exists(file)&&mode.Name=="chambers")file=Path.Combine(root,"runtime","companion-session.txt");
  if(!File.Exists(file))return false;
  string text=File.ReadAllText(file);string[] parts=text.Split(new char[]{'\r','\n'},StringSplitOptions.RemoveEmptyEntries);int pid,port;
  if(parts.Length!=4||parts[0]!="OWL_RELOAD_V1"||!int.TryParse(parts[1],out pid)||!int.TryParse(parts[2],out port)||port<1||port>65535||parts[3].Length!=43)throw new Exception("Invalid companion session record; no additional client was started.");
  Process active=null;try{active=Process.GetProcessById(pid);if(active.HasExited)active=null;}catch(ArgumentException){}
  if(active==null){if(File.Exists(file)&&File.ReadAllText(file)==text)File.Delete(file);return false;}
  using(var socket=new TcpClient()) {
   var connection=socket.BeginConnect(IPAddress.Loopback,port,null,null);if(!connection.AsyncWaitHandle.WaitOne(3000))throw new Exception("The active "+mode.Name+" reload host did not respond. No additional client was started.");socket.EndConnect(connection);
   socket.ReceiveTimeout=45000;socket.SendTimeout=3000;
   using(var stream=socket.GetStream())using(var writer=new StreamWriter(stream,new UTF8Encoding(false),1024,true))using(var reader=new StreamReader(stream,Encoding.UTF8,false,1024,true)){
    writer.NewLine="\n";writer.WriteLine("RELOAD "+parts[3]);writer.Flush();string response=BoundedLine(reader);if(response==null||!response.StartsWith("OK ",StringComparison.Ordinal))throw new Exception(response??"The client closed its reload connection. No additional client was started.");
   }
  }return true;
 }
 static string BoundedLine(TextReader input) {var line=new StringBuilder();for(int i=0;i<1024;i++){int c=input.Read();if(c<0)return line.Length==0?null:line.ToString();if(c=='\n')return line.ToString();if(c!='\r')line.Append((char)c);}throw new IOException("Unexpected reload response.");}
 static bool ExistingOrion() {foreach(var process in Process.GetProcesses())using(process){try{string name=process.ProcessName;if((name.Equals("javaw",StringComparison.OrdinalIgnoreCase)||name.Equals("java",StringComparison.OrdinalIgnoreCase)||name.Equals("Orion",StringComparison.OrdinalIgnoreCase))&&process.MainWindowTitle.StartsWith("Orion",StringComparison.OrdinalIgnoreCase))return true;}catch(System.ComponentModel.Win32Exception){}catch(InvalidOperationException){}}return false;}
 [STAThread] static int Main(string[] args) {
  bool checks=Array.IndexOf(args,"--self-test")>=0;LaunchMode mode=null;
  try {
   mode=Mode(args);
   if(checks){SelfTest();Console.WriteLine("Launcher checks passed: separated feature modes, release/session routing, same-process reload and bounded protocol.");return 0;}
   string root=AppDomain.CurrentDomain.BaseDirectory;
   using(var gate=new Mutex(false,MutexName(root,mode.Name))){bool held=false;try{try{held=gate.WaitOne(0);}catch(AbandonedMutexException){held=true;}if(!held)return 0;
    if(Reload(root,mode))return 0;
    if(ExistingOrion())throw new Exception("Orion is already open with a different or legacy Turbo feature host. Close it once, then open "+Path.GetFileName(Application.ExecutablePath)+". The existing client was left running.");
    string runtime=JavaRuntime(root);
    string client=Artifact(root,"release-runtime.txt",Path.Combine("runtime","orion-client.jar")),companion=Artifact(root,mode.Pointer,mode.Fallback);
    if(!File.Exists(runtime))throw new Exception("Java runtime not found: "+runtime+". Install and launch official Orion once, then run Setup.cmd or the Turbo updater.");if(!File.Exists(client))throw new Exception("Orion client JAR not found: "+client+". Run Setup.cmd or the Turbo updater after installing and launching official Orion.");if(!File.Exists(companion))throw new Exception(mode.Name+" companion JAR not found: "+companion+". Rebuild or update the complete Turbo package.");
    string profile=Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),logging=Path.Combine(root,"prototype-logback.xml"),session=mode.Session.Replace('/','\\');
    string arguments="-Xmx1g -Xss2m -XX:+DisableAttachMechanism \"-Duser.home="+profile+"\" \"-Dowl.project.root="+root.TrimEnd(Path.DirectorySeparatorChar)+"\" \"-Dowl.client.mode="+mode.Name+"\" \"-Dowl.release.pointer="+mode.Pointer+"\" \"-Dowl.companion.session="+session+"\" \"-Dlogback.configurationFile="+logging+"\" -cp \""+companion+";"+client+"\" owl.nh.Bootstrap";
    using(var process=Process.Start(new ProcessStartInfo {FileName=runtime,Arguments=arguments,WorkingDirectory=root,UseShellExecute=false,CreateNoWindow=true})){
     if(process==null)throw new Exception("Could not start Orion "+mode.Name+" companion.");var timer=Stopwatch.StartNew();string sessionFile=Path.Combine(root,session);while(timer.ElapsedMilliseconds<90000){if(process.HasExited)throw new Exception("Orion exited during "+mode.Name+" startup.");if(File.Exists(sessionFile))return 0;Thread.Sleep(200);}throw new Exception("Orion is still starting, but its "+mode.Name+" reload host is not ready. No second client was started.");
    }
   }finally{if(held)gate.ReleaseMutex();}}
  } catch(Exception e) {if(checks)Console.Error.WriteLine(e);else MessageBox.Show(e.Message,mode==null?"Turbo Client startup":mode.Title,MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;}
 }
 static void Check(bool value,string name){if(!value)throw new Exception("Launcher check failed: "+name);}
 static void SelfTest(){
  LaunchMode chambers=Mode(new[]{"--mode=chambers"}),slayer=Mode(new[]{"--mode=slayer"}),toa=Mode(new[]{"--mode=toa"}),gauntlet=Mode(new[]{"--mode=gauntlet"}),admin=Mode(new[]{"--mode=admin"});
  Check(chambers.Pointer!=slayer.Pointer&&slayer.Pointer!=toa.Pointer&&toa.Pointer!=gauntlet.Pointer&&gauntlet.Pointer!=admin.Pointer&&chambers.Session!=slayer.Session&&slayer.Session!=toa.Session&&toa.Session!=gauntlet.Session&&gauntlet.Session!=admin.Session,"features use distinct release pointers and session files");
  Check(MutexName("C:\\Turbo","chambers")!=MutexName("C:\\Turbo","slayer"),"features use distinct launcher gates");
  string root=Path.Combine(Path.GetTempPath(),"owl-launcher-check-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(Path.Combine(root,"runtime"));var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();Exception workerError=null;
  try {int port=((IPEndPoint)listener.LocalEndpoint).Port;string token=new string('x',43);string file=Path.Combine(root,chambers.Session.Replace('/',Path.DirectorySeparatorChar));File.WriteAllText(file,"OWL_RELOAD_V1\n"+Process.GetCurrentProcess().Id+"\n"+port+"\n"+token+"\n");
   var worker=new Thread(()=>{try{using(var socket=listener.AcceptTcpClient())using(var stream=socket.GetStream())using(var input=new StreamReader(stream,Encoding.UTF8,false,1024,true))using(var output=new StreamWriter(stream,new UTF8Encoding(false),1024,true)){Check(input.ReadLine()=="RELOAD "+token,"authenticated reload command");output.WriteLine("OK reloaded");output.Flush();}}catch(Exception error){workerError=error;}});worker.IsBackground=true;worker.Start();Check(Reload(root,chambers),"active Chambers session routes to Chambers reload");Check(worker.Join(5000)&&workerError==null,"reload exchange completed");Check(!Reload(root,slayer),"Slayer never reuses Chambers session");
   Check(!Reload(root,admin),"Admin Tool never reuses a fighter session");File.WriteAllText(file,"OWL_RELOAD_V1\n2147483647\n"+port+"\n"+token+"\n");Check(!Reload(root,chambers)&&!File.Exists(file),"stale process record removed");bool rejected=false;try{BoundedLine(new StringReader(new string('x',1025)));}catch(IOException){rejected=true;}Check(rejected,"oversized response rejected");
  }finally{listener.Stop();Directory.Delete(root,true);}
 }
}
