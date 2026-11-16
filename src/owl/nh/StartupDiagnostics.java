package owl.nh;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

/** Keeps only startup exceptions in memory; no gameplay logs or files. */
final class StartupDiagnostics extends AppenderBase<ILoggingEvent> {
 volatile Throwable failure;private Logger root;
 void begin(){root=(Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);setContext(root.getLoggerContext());start();root.addAppender(this);root.setLevel(Level.ERROR);}
 void end(){if(root!=null){root.detachAppender(this);root.setLevel(Level.OFF);}stop();}
 @Override protected void append(ILoggingEvent event){if(event.getThrowableProxy() instanceof ThrowableProxy)failure=((ThrowableProxy)event.getThrowableProxy()).getThrowable();}
 static String detail(Throwable failure) {StringBuilder text=new StringBuilder();int depth=0;while(failure!=null&&depth++<8){text.append(failure.getClass().getSimpleName()).append(": ").append(failure.getMessage()).append('\n');int frames=0;for(StackTraceElement frame:failure.getStackTrace())if(frame.getClassName().startsWith("owl.nh")||frame.getClassName().startsWith("net.runelite")){text.append("  at ").append(frame).append('\n');if(++frames==4)break;}failure=failure.getCause();}return text.toString();}
 static String classes(Throwable failure){StringBuilder text=new StringBuilder();int depth=0;while(failure!=null&&depth++<8){text.append(failure.getClass().getName()).append('\n');for(StackTraceElement frame:failure.getStackTrace())if(frame.getClassName().startsWith("net.runelite")||frame.getClassName().startsWith("owl.nh")){text.append(" at ").append(frame).append('\n');break;}failure=failure.getCause();}return text.toString();}
}
