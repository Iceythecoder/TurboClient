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
 static string Artifact(string root,string marker,string fallback) {
  string pointer=Path.Combine(root,marker);string relative=File.Exists(pointer)?File.ReadAllText(pointer).Trim():fallback;
  string resolved=Path.GetFullPath(Path.Combine(root,relative));string boundary=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar;
  if(!resolved.StartsWith(boundary,StringComparison.OrdinalIgnoreCase)||relative.IndexOf('"')>=0)throw new Exception("Invalid local release selection.");return resolved;
 }
 static string MutexName(string root) {using(var hash=SHA256.Create())return "Local\\OwlOrionCompanion-"+BitConverter.ToString(hash.ComputeHash(Encoding.UTF8.GetBytes(Path.GetFullPath(root).ToUpperInvariant()))).Replace("-","").Substring(0,24);}
 static bool Reload(string root) {
  string file=Path.Combine(root,"runtime","companion-session.txt");if(!File.Exists(file))return false;
  string text=File.ReadAllText(file);string[] parts=text.Split(new char[]{'\r','\n'},StringSplitOptions.RemoveEmptyEntries);int pid,port;
  if(parts.Length!=4||parts[0]!="OWL_RELOAD_V1"||!int.TryParse(parts[1],out pid)||!int.TryParse(parts[2],out port)||port<1||port>65535||parts[3].Length!=43)throw new Exception("Invalid companion session record; no additional client was started.");
  Process active=null;try{active=Process.GetProcessById(pid);if(active.HasExited)active=null;}catch(ArgumentException){}
  if(active==null){if(File.Exists(file)&&File.ReadAllText(file)==text)File.Delete(file);return false;}
  using(var socket=new TcpClient()) {
   var connection=socket.BeginConnect(IPAddress.Loopback,port,null,null);if(!connection.AsyncWaitHandle.WaitOne(3000))throw new Exception("The active client's reload host did not respond. No additional client was started.");socket.EndConnect(connection);
   socket.ReceiveTimeout=45000;socket.SendTimeout=3000;
   using(var stream=socket.GetStream())using(var writer=new StreamWriter(stream,new UTF8Encoding(false),1024,true))using(var reader=new StreamReader(stream,Encoding.UTF8,false,1024,true)){
    writer.NewLine="\n";writer.WriteLine("RELOAD "+parts[3]);writer.Flush();string response=BoundedLine(reader);if(response==null||!response.StartsWith("OK ",StringComparison.Ordinal))throw new Exception(response??"The client closed its reload connection. No additional client was started.");
   }
  }return true;
 }
 static string BoundedLine(TextReader input) {var line=new StringBuilder();for(int i=0;i<1024;i++){int c=input.Read();if(c<0)return line.Length==0?null:line.ToString();if(c=='\n')return line.ToString();if(c!='\r')line.Append((char)c);}throw new IOException("Unexpected reload response.");}
 static bool ExistingOrion() {foreach(var process in Process.GetProcesses())using(process){try{string name=process.ProcessName;if((name.Equals("javaw",StringComparison.OrdinalIgnoreCase)||name.Equals("java",StringComparison.OrdinalIgnoreCase)||name.Equals("Orion",StringComparison.OrdinalIgnoreCase))&&process.MainWindowTitle.StartsWith("Orion",StringComparison.OrdinalIgnoreCase))return true;}catch(System.ComponentModel.Win32Exception){}catch(InvalidOperationException){}}return false;}
 [STAThread] static int Main(string[] args) {
  bool checks=Array.IndexOf(args,"--self-test")>=0;
  try {
   if(checks){SelfTest();Console.WriteLine("Launcher checks passed: same-process reload request, no duplicate launch, stale session and bounded protocol.");return 0;}
   string root=AppDomain.CurrentDomain.BaseDirectory;
   using(var gate=new Mutex(false,MutexName(root))){bool held=false;try{try{held=gate.WaitOne(0);}catch(AbandonedMutexException){held=true;}if(!held)return 0;
    if(Reload(root))return 0;
    if(ExistingOrion())throw new Exception("Orion is already open, but this client has no companion reload host. Close it once and reopen Turbo Client.exe to enable in-window companion reloads. Your current client was left running.");
    string runtime=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Orion","jre","bin","javaw.exe");
    string client=Artifact(root,"release-runtime.txt",Path.Combine("runtime","orion-client.jar")),companion=Artifact(root,"release-current.txt","OrionNH.jar");
    if(!File.Exists(runtime)||!File.Exists(client)||!File.Exists(companion))throw new Exception("Missing Java runtime or build artifacts. Run build.ps1 first. Orion must be installed.");
    string profile=Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),logging=Path.Combine(root,"prototype-logback.xml");
    using(var process=Process.Start(new ProcessStartInfo {FileName=runtime,Arguments="-Xmx1g -Xss2m -XX:+DisableAttachMechanism \"-Duser.home="+profile+"\" \"-Dowl.project.root="+root.TrimEnd(Path.DirectorySeparatorChar)+"\" \"-Dlogback.configurationFile="+logging+"\" -cp \""+companion+";"+client+"\" owl.nh.Bootstrap",WorkingDirectory=root,UseShellExecute=false,CreateNoWindow=true})){
     if(process==null)throw new Exception("Could not start Orion companion.");var timer=Stopwatch.StartNew();while(timer.ElapsedMilliseconds<90000){if(process.HasExited)throw new Exception("Orion exited during startup.");if(File.Exists(Path.Combine(root,"runtime","companion-session.txt")))return 0;Thread.Sleep(200);}throw new Exception("Orion is still starting, but its reload host is not ready. No second client was started.");
    }
   }finally{if(held)gate.ReleaseMutex();}}
  } catch(Exception e) {if(checks)Console.Error.WriteLine(e);else MessageBox.Show(e.Message,"Turbo Client startup",MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;}
 }
 static void Check(bool value,string name){if(!value)throw new Exception("Launcher check failed: "+name);}
 static void SelfTest(){
  string root=Path.Combine(Path.GetTempPath(),"owl-launcher-check-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(Path.Combine(root,"runtime"));var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();Exception workerError=null;
  try {int port=((IPEndPoint)listener.LocalEndpoint).Port;string token=new string('x',43);string file=Path.Combine(root,"runtime","companion-session.txt");File.WriteAllText(file,"OWL_RELOAD_V1\n"+Process.GetCurrentProcess().Id+"\n"+port+"\n"+token+"\n");
   var worker=new Thread(()=>{try{using(var socket=listener.AcceptTcpClient())using(var stream=socket.GetStream())using(var input=new StreamReader(stream,Encoding.UTF8,false,1024,true))using(var output=new StreamWriter(stream,new UTF8Encoding(false),1024,true)){Check(input.ReadLine()=="RELOAD "+token,"authenticated reload command");output.WriteLine("OK reloaded");output.Flush();}}catch(Exception error){workerError=error;}});worker.IsBackground=true;worker.Start();Check(Reload(root),"active session routes to reload");Check(worker.Join(5000)&&workerError==null,"reload exchange completed");
   File.WriteAllText(file,"OWL_RELOAD_V1\n2147483647\n"+port+"\n"+token+"\n");Check(!Reload(root)&&!File.Exists(file),"stale process record removed");Check(!Reload(root),"absent session allows first startup");bool rejected=false;try{BoundedLine(new StringReader(new string('x',1025)));}catch(IOException){rejected=true;}Check(rejected,"oversized response rejected");Check(MutexName(root)==MutexName(root.ToUpperInvariant()),"workspace launcher gate is case insensitive");
  }finally{listener.Stop();Directory.Delete(root,true);}
 }
}