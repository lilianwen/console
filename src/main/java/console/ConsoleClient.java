package console;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fisco.bcos.web3j.protocol.ObjectMapperFactory;
import org.fisco.bcos.web3j.protocol.channel.ResponseExcepiton;
import org.fisco.bcos.web3j.protocol.core.Response;
import org.fisco.bcos.web3j.protocol.exceptions.MessageDecodingException;
import org.jline.builtins.Completers.FilesCompleter;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import console.common.Common;
import console.common.ConsoleUtils;
import console.common.HelpInfo;

public class ConsoleClient {
  @SuppressWarnings("resource")
  public static void main(String[] args) {
  	ConsoleFace console = null;
  	LineReader lineReader = null;
  	try {	
  		
  		console = new ConsoleImpl();
     	console.init(args);
    	console.welcome();
      Path path = FileSystems.getDefault().getPath("solidity/contracts/", "");

      List<Completer> completers = new ArrayList<Completer>();
      completers.add(new ArgumentCompleter(new StringsCompleter("help")));
      completers.add(new ArgumentCompleter(new StringsCompleter("address")));
      completers.add(new ArgumentCompleter(new StringsCompleter("deploy"), new FilesCompleter(path)));
      completers.add(new ArgumentCompleter(new StringsCompleter("call"), new FilesCompleter(path)));
      completers.add(new ArgumentCompleter(new StringsCompleter("quit")));
      completers.add(new ArgumentCompleter(new StringsCompleter("exit")));

      Terminal terminal = TerminalBuilder.terminal();
      lineReader =
          LineReaderBuilder.builder()
              .terminal(terminal)
              .completer(new AggregateCompleter(completers))
              .build();
  	 }catch (Exception e) {
        System.out.println(e.getMessage());
        return;
     } 
      
     while (true) {
    	 
    	try {
		    String request = lineReader.readLine("[group:"+ConsoleImpl.groupID+"]> ");
		    String[] params = null;
				params = ConsoleUtils.tokenizeCommand(request);
	      if (params.length < 1) {
	        System.out.print("");
	        continue;
	      }
	      if ("".equals(params[0].trim())) {
	        System.out.print("");
	        continue;
	      }
	      if ("quit".equals(params[0]) || "q".equals(params[0]) || "exit".equals(params[0])) {
	        if (HelpInfo.promptNoParams(params, "q")) {
	          continue;
	        } else if (params.length > 2) {
	          HelpInfo.promptHelp("q");
	          continue;
	        }
	        console.close();
	        break;
	      }

        if ("address".equals(params[0])){
          console.address();
          continue;
        }
        switch (params[0]) {
          case "help":
          case "h":
            console.help(params);
            break;
          case "deploy":
            console.deploy(params);
            break;
            
          case "call":
            console.call(params);
            break;
          default:
            System.out.println("Undefined command: \"" + params[0] + "\". Try \"help\".\n");
            break;
        }
	  
			}catch (ResponseExcepiton e) {
        ConsoleUtils.printJson(
            "{\"code\":" + e.getCode() + ", \"msg\":" + "\"" + e.getMessage() + "\"}");
        System.out.println();
      } catch (ClassNotFoundException e) {
        System.out.println(e.getMessage() + " does not exist.");
        System.out.println();
      } catch (MessageDecodingException e) {
        pringMessageDecodeingException(e);
      }catch (IOException e) {
        if (e.getMessage().startsWith("activeConnections")) {
					System.out.println("Lost the connection to the node. " 
							+ "Please check the connection between the console and the node.");
        } else if (e.getMessage().startsWith("No value")) {
          System.out.println(
              "The groupID is not configured in dist/conf/applicationContext.xml file.");
        } else {
          System.out.println(e.getMessage());
        }
        System.out.println();
      } 
    	catch (InvocationTargetException e) {
    		System.out.println("Contract call failed.");
    		System.out.println();
    	}
      catch (Exception e) {
      	if(e.getMessage().contains("MessageDecodingException"))
      	{
      		pringMessageDecodeingException(new MessageDecodingException(e.getMessage().split("MessageDecodingException: ")[1]));
      	}
      	else {
      		System.out.println(e.getMessage());
      		System.out.println();
      	}
      } 
     }
  }

	private static void pringMessageDecodeingException(MessageDecodingException e) {
		String message = e.getMessage();
		Response t = null;
		try {
		    t = ObjectMapperFactory.getObjectMapper(true).readValue(
		                message.substring(message.indexOf("{"), message.lastIndexOf("}") + 1),
		                Response.class);
		    if (t != null) {
		      ConsoleUtils.printJson(
		          "{\"code\":"
		              + t.getError().getCode()
		              + ", \"msg\":"
		              + "\""
		              + t.getError().getMessage()
		              + "\"}");
		      System.out.println();
		    }
		  }catch (Exception e1) {
		    System.out.println(e1.getMessage());
		    System.out.println();
    }
	}
}
