package console;

import static console.common.ContractClassFactory.getContractClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fisco.bcos.channel.client.Service;
import org.fisco.bcos.channel.handler.ChannelConnections;
import org.fisco.bcos.channel.handler.GroupChannelConnectionsConfig;
import org.fisco.bcos.web3j.crypto.Credentials;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.crypto.Keys;
import org.fisco.bcos.web3j.crypto.gm.GenCredential;
import org.fisco.bcos.web3j.precompile.cns.CnsInfo;
import org.fisco.bcos.web3j.precompile.cns.CnsService;
import org.fisco.bcos.web3j.precompile.common.PrecompiledCommon;
import org.fisco.bcos.web3j.precompile.config.SystemConfigSerivce;
import org.fisco.bcos.web3j.precompile.consensus.ConsensusService;
import org.fisco.bcos.web3j.precompile.permission.PermissionInfo;
import org.fisco.bcos.web3j.precompile.permission.PermissionService;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.channel.ChannelEthereumService;
import org.fisco.bcos.web3j.protocol.channel.ResponseExcepiton;
import org.fisco.bcos.web3j.protocol.core.DefaultBlockParameter;
import org.fisco.bcos.web3j.protocol.core.DefaultBlockParameterName;
import org.fisco.bcos.web3j.protocol.core.RemoteCall;
import org.fisco.bcos.web3j.protocol.core.methods.response.BcosBlock;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.Contract;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;
import org.fisco.bcos.web3j.utils.Numeric;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractRefreshableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.alibaba.fastjson.JSONObject;

import console.common.Address;
import console.common.Common;
import console.common.ConsoleUtils;
import console.common.ConsoleVersion;
import console.common.ContractClassFactory;
import console.common.HelpInfo;
import console.exception.CompileSolidityException;
import console.exception.ConsoleMessageException;
import io.bretty.console.table.Alignment;
import io.bretty.console.table.ColumnFormatter;
import io.bretty.console.table.Table;

public class ConsoleImpl implements ConsoleFace {
	
	private static ChannelEthereumService channelEthereumService;
    private static Web3j web3j = null;
    private ApplicationContext context;
    private static java.math.BigInteger gasPrice = new BigInteger("10");
    private static java.math.BigInteger gasLimit = new BigInteger("50000000");
    private ECKeyPair keyPair;
    private static Credentials credentials;
    private String contractAddress;
    private String contractName;
    private String contractVersion;
    private Class<?> contractClass;
    private RemoteCall<?> remoteCall;
    private String privateKey = "";
    public static int groupID;
    public static final int InvalidRequest = 40009;
    
    public void init(String[] args) {
    	context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
    	Service service = context.getBean(Service.class);
        groupID = service.getGroupId();
        if (args.length < 2) {
            InputStream is = null;
            OutputStream os = null;
            try {
                // read private key from privateKey.properties
                Properties prop = new Properties();
                Resource keyResource = new ClassPathResource("privateKey.properties");
                if (!keyResource.exists()) {
                    File privateKeyDir = new File("conf/privateKey.properties");
                    privateKeyDir.createNewFile();
                    keyResource = new ClassPathResource("privateKey.properties");
                }
                is = keyResource.getInputStream();
                prop.load(is);
                privateKey = prop.getProperty("privateKey");
                is.close();
                if (privateKey == null) {
                    // save private key in privateKey.properties
                    keyPair = Keys.createEcKeyPair();
                    privateKey = keyPair.getPrivateKey().toString(16);
                    prop.setProperty("privateKey", privateKey);
                    os = new FileOutputStream(keyResource.getFile());
                    prop.store(os, "private key");
                    os.close();
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                close();
            }
        }
        switch (args.length) {
            case 0:
                break;
            case 1:
                groupID = setGroupID(args, groupID);
                break;
            default:
                groupID = setGroupID(args, groupID);
                privateKey = args[1];
                break;
        }
        try {
            credentials = GenCredential.create(privateKey);
        } catch (NumberFormatException e) {
            System.out.println("Please provide private key by hex format.");
            close();
        }
        service.setGroupId(groupID);
        try {
            service.run();
        } catch (Exception e) {
            System.out.println(
                    "Failed to connect to the node. Please check the node status and the console configruation.");
            close();
        }
        channelEthereumService = new ChannelEthereumService();
        channelEthereumService.setChannelService(service);
        channelEthereumService.setTimeout(60000);
        web3j = Web3j.build(channelEthereumService, groupID);
        try {
            web3j.getBlockNumber().sendForReturnString();
        } catch (ResponseExcepiton e) {
            if (e.getCode() == InvalidRequest) {
                System.out.println("Don't connect a removed node.");
            } else {
                System.out.println(e.getMessage());
            }
            close();
        } catch (Exception e) {
            System.out.println(
                    "Failed to connect to the node. Please check the node status and the console configruation.");
            close();
        }
    }

    private int setGroupID(String[] args, int groupID) {
        try {
            groupID = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Please provide groupID by integer format.");
            close();
        }
        return groupID;
    }
    
    @Override
    public void close() {
      try {
        if (channelEthereumService != null) {
            channelEthereumService.close();
        }
        System.exit(0);
    } catch (IOException e) {
        System.out.println(e.getMessage());
    }
    
    }
    @Override
    public void welcome() {
        ConsoleUtils.doubleLine();
        System.out.println("Welcome to FISCO BCOS console(" + ConsoleVersion.Version + ")!");
        System.out.println("Type 'help' or 'h' for help. Type 'quit' or 'q' or 'exit' to quit console.");
        String logo =
                " ________ ______  ______   ______   ______       _______   ______   ______   ______  \n"
                        + "|        |      \\/      \\ /      \\ /      \\     |       \\ /      \\ /      \\ /      \\ \n"
                        + "| $$$$$$$$\\$$$$$|  $$$$$$|  $$$$$$|  $$$$$$\\    | $$$$$$$|  $$$$$$|  $$$$$$|  $$$$$$\\\n"
                        + "| $$__     | $$ | $$___\\$| $$   \\$| $$  | $$    | $$__/ $| $$   \\$| $$  | $| $$___\\$$\n"
                        + "| $$  \\    | $$  \\$$    \\| $$     | $$  | $$    | $$    $| $$     | $$  | $$\\$$    \\ \n"
                        + "| $$$$$    | $$  _\\$$$$$$| $$   __| $$  | $$    | $$$$$$$| $$   __| $$  | $$_\\$$$$$$\\\n"
                        + "| $$      _| $$_|  \\__| $| $$__/  | $$__/ $$    | $$__/ $| $$__/  | $$__/ $|  \\__| $$\n"
                        + "| $$     |   $$ \\\\$$    $$\\$$    $$\\$$    $$    | $$    $$\\$$    $$\\$$    $$\\$$    $$\n"
                        + " \\$$      \\$$$$$$ \\$$$$$$  \\$$$$$$  \\$$$$$$      \\$$$$$$$  \\$$$$$$  \\$$$$$$  \\$$$$$$";
        System.out.println(logo);
        System.out.println();
        ConsoleUtils.doubleLine();
    }

    @Override
    public void help(String[] params) {
        if (HelpInfo.promptNoParams(params, "help")) {
            return;
        }
        if (params.length > 2) {
            HelpInfo.promptHelp("help");
            return;
        }
        ConsoleUtils.singleLine();
        StringBuilder sb = new StringBuilder();
        sb.append("help(h)                                  Provide help information.\n");
        sb.append("deploy                                   Deploy a contract on blockchain.\n");
        sb.append(
                "call                                     Call a contract by a function and paramters.\n");
        sb.append("quit(q)                                  Quit console.\n");
        sb.append("exit                                     Quit console.");
        System.out.println(sb.toString());
        ConsoleUtils.singleLine();
        System.out.println();
    }

    private void handleDeployParameters(String[] params, int num) throws IllegalAccessException, InvocationTargetException, ConsoleMessageException {
            Method method = ContractClassFactory.getDeployFunction(contractClass);
            Type[] classType = method.getParameterTypes();
            if(classType.length - 3  != params.length - num) {
                throw new ConsoleMessageException("The number of paramters does not match!");
            }
            String[] generic = new String[method.getParameterCount()];
            for (int i = 0; i < classType.length; i++) {
                generic[i] = method.getGenericParameterTypes()[i].getTypeName();
            }
            Class[] classList = new Class[classType.length];
            for (int i = 0; i < classType.length; i++) {
                Class clazz = (Class) classType[i];
                classList[i] = clazz;
            }

            String[] newParams = new String[params.length - num];
            System.arraycopy(params, num, newParams, 0, params.length - num);
            Object[] obj = getDeployPrametersObject("deploy", classList, newParams, generic);
            remoteCall = (RemoteCall<?>) method.invoke(null, obj);
    }

    public static Object[] getDeployPrametersObject(String funcName, Class[] type, String[] params, String[] generic) throws ConsoleMessageException {
        Object[] obj = new Object[params.length + 3];
        obj[0] = web3j;
        obj[1] = credentials;
        obj[2] = new StaticGasProvider(gasPrice, gasLimit);

        for (int i = 0; i < params.length; i++) {
            if (type[i + 3] == String.class) {
                if (params[i].startsWith("\"") && params[i].endsWith("\"")) {
                    obj[i + 3] = params[i].substring(1, params[i].length() - 1);
                }
                else 
                {
                  throw new ConsoleMessageException("The " + (i + 1) + "th parameter of " + funcName + " needs string value.");
                }
            } else if (type[i + 3] == Boolean.class) {
                try {
                    obj[i + 3] = Boolean.parseBoolean(params[i]);
                } catch (Exception e) {
                    throw new ConsoleMessageException("The " + (i + 1) + "th parameter of " + funcName + " needs boolean value.");
                }
            } else if (type[i + 3] == BigInteger.class) {
                try {
                    obj[i + 3] = new BigInteger(params[i]);
                } catch (Exception e) {
                    throw new ConsoleMessageException("The " + (i + 1) + "th parameter of " + funcName + " needs integer value.");
                }
            } else if (type[i + 3] == byte[].class) {
                if (params[i].startsWith("\"") && params[i].endsWith("\"")) {
                    byte[] bytes2 = params[i + 3].substring(1, params[i + 3].length() - 1).getBytes();
                    byte[] bytes1 = new byte[bytes2.length];
                    for (int j = 0; j < bytes2.length; j++) {
                        bytes1[j] = bytes2[j];
                    }
                    obj[i + 3] = bytes1;
                } else {
                    throw new ConsoleMessageException("The " + (i + 1) + "th parameter of " + funcName + " needs byte string value.");
                }
            } else if (type[i + 3] == List.class) {

                if (params[i].startsWith("[") && params[i].endsWith("]")) {
                    String listParams = params[i].substring(1, params[i].length() - 1);
                    String[] ilist = listParams.split(",");
                    String[] jlist = new String[ilist.length];
                    for(int k = 0; k < jlist.length; k++)
                    {
                        jlist[k] = ilist[k].trim();
                    }
                    List paramsList = new ArrayList();
                    if (generic[i].contains("String")) {
                        paramsList = new ArrayList<String>();
                        for (int j = 0; j < jlist.length; j++) {
                            paramsList.add(jlist[j].substring(1, jlist[j].length() - 1));
                        }

                    } else if (generic[i].contains("BigInteger")) {
                        paramsList = new ArrayList<BigInteger>();
                        for (int j = 0; j < jlist.length; j++) {
                            paramsList.add(new BigInteger(jlist[j]));
                        }

                    }
                    else if(generic[i].contains("byte[]")) {
                        paramsList = new ArrayList<byte[]>();
                        for (int j = 0; j < jlist.length; j++) {
                            if (jlist[j].startsWith("\"") && jlist[j].endsWith("\"")) {
                                byte[] bytes = jlist[j].substring(1, jlist[j].length() - 1).getBytes();
                                byte[] bytes1 = new byte[32];
                                byte[] bytes2 = bytes;
                                for (int k = 0; k < bytes2.length; k++) {
                                    bytes1[k] = bytes2[k];
                                }
                                paramsList.add(bytes1);
                            }
                        }
                    }
                    obj[i + 3] = paramsList;
                }
                else 
                {
                    throw new ConsoleMessageException("The " + (i + 1) + "th parameter of " + funcName + " needs array value.");
                }
            }
        }
        return obj;
    }

        synchronized private void writeLog() {
        
        BufferedReader reader = null;
        try {
            File logFile = new File("deploylog.txt");
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
                reader = new BufferedReader(new FileReader("deploylog.txt"));
                String line;
                List<String> textList = new ArrayList<String>();
                while ((line = reader.readLine()) != null) {
                        textList.add(line);
                }
                int i = 0;
                if (textList.size() >= Common.LogMaxCount) {
                    i = textList.size() - Common.LogMaxCount + 1;
          if(logFile.exists()){
              logFile.delete();
              logFile.createNewFile();
          }
          PrintWriter pw = new PrintWriter(new FileWriter("deploylog.txt",true));
          for(; i < textList.size(); i++)
          {
            pw.println(textList.get(i));
          }
          pw.flush();
          pw.close();
                }
            }
            catch(IOException e)
            {
                System.out.println("Read deploylog.txt failed.");
                return;
            }
            finally 
            {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.out.println("Close deploylog.txt failed.");;
                }
            }
       DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

       String name =  contractName.substring(20);
       while(name.length() < 20){
           name = name + " ";
       }
        String log = LocalDateTime.now().format(formatter) + "  [group:"+ groupID +  "]  " + name + "  " + contractAddress;
        try {
            File logFile =  new File("deploylog.txt");
            if(!logFile.exists()){
                logFile.createNewFile();
            }
            PrintWriter pw = new PrintWriter(new FileWriter("deploylog.txt",true));
            pw.println(log);
            pw.flush();
            pw.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println();
            return;
        }
    }


    @Override
    public void deploy(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("deploy");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.deployHelp();
            return;
        }
        String name = params[1];
        if (name.endsWith(".sol")) {
            name = name.substring(0, name.length() - 4);
        }
        try {
        	ConsoleUtils.dynamicCompileSolFilesToJava(name);
        }catch (CompileSolidityException e) {
        	System.out.println(e.getMessage());
        	return;
        }catch (IOException e) {
        	System.out.println(e.getMessage());
        	System.out.println();
        	return;
        }
        ConsoleUtils.dynamicCompileJavaToClass(name);
        contractName = ConsoleUtils.PACKAGENAME + "." + name;
        try {
            contractClass = getContractClass(contractName);
        } catch (Exception e) {
            System.out.println(
                    "There is no " + name + ".class" + " in the directory of solidity/java/classes/org/fisco/bcos/temp/.");
            System.out.println();
            return;
        }
        try {
					handleDeployParameters(params, 2);
				} catch (ConsoleMessageException e) {
					System.out.println(e.getMessage());
					System.out.println();
					return;
				}
        try {
        	Contract contract = (Contract) remoteCall.send();
      	  contractAddress = contract.getContractAddress();
          System.out.println(contractAddress);
          System.out.println();
          contractAddress = contract.getContractAddress();
          writeLog();
        } catch (Exception e) {
            if (e.getMessage().contains("0x19")) {
                ConsoleUtils.printJson(PrecompiledCommon.transferToJson(PrecompiledCommon.PermissionDenied));
            } else {
                throw e;
            }
        }

    }

    @Override
    public void call(String[] params) throws Exception {
        if (params.length < 2) {
            HelpInfo.promptHelp("call");
            return;
        }
        if ("-h".equals(params[1]) || "--help".equals(params[1])) {
            HelpInfo.callHelp();
            return;
        }
        if (params.length < 4) {
            HelpInfo.promptHelp("call");
            return;
        }
        String name = params[1];
        if (name.endsWith(".sol")) {
            name = name.substring(0, name.length() - 4);
        }
        contractName = ConsoleUtils.PACKAGENAME + "." + name;
        try {
            contractClass = getContractClass(contractName);
        } catch (Exception e) {
            System.out.println(
                    "There is no "
                            + name
                            + ".class"
                            + " in the directory of java/classes/org/fisco/bcos/temp");
            System.out.println();
            return;
        }
        Method load =
                contractClass.getMethod(
                        "load",
                        String.class,
                        Web3j.class,
                        Credentials.class,
                        BigInteger.class,
                        BigInteger.class);
        Object contractObject;

        contractAddress = params[2];
        Address convertAddr = ConsoleUtils.convertAddress(contractAddress);
        if (!convertAddr.isValid()) {
            return;
        }
        contractAddress = convertAddr.getAddress();
        contractObject = load.invoke(null, contractAddress, web3j, credentials, gasPrice, gasLimit);
        String funcName = params[3];
        Method[] methods = contractClass.getDeclaredMethods();
        Method method = ContractClassFactory.getMethodByName(funcName, methods);
        if(method == null) {
        	System.out.println("Cannot find the method. Please checkout the method name.");
        	System.out.println();
        	return;
        }
        String[] generic = new String[method.getParameterCount()];
        Type[] classType = method.getParameterTypes();
        for (int i = 0; i < classType.length; i++) {
            generic[i] = method.getGenericParameterTypes()[i].getTypeName();
        }
        Class[] classList = new Class[classType.length];
        for (int i = 0; i < classType.length; i++) {
            Class clazz = (Class) classType[i];
            classList[i] = clazz;
        }
        Class[] parameterType =
                ContractClassFactory.getParameterType(contractClass, funcName, params.length - 4);
        if (parameterType == null) {
            HelpInfo.promptNoFunc(params[1], funcName, params.length - 4);
            return;
        }
        Method func = contractClass.getMethod(funcName, parameterType);
        String[] newParams = new String[params.length - 4];
        System.arraycopy(params, 4, newParams, 0, params.length - 4);
        Object[] argobj = ContractClassFactory.getPrametersObject(funcName, parameterType, newParams, generic);
        if (argobj == null) {
            return;
        }
        remoteCall = (RemoteCall<?>) func.invoke(contractObject, argobj);
        Object result;
				result = remoteCall.send();
				if(result instanceof TransactionReceipt)
				{
					TransactionReceipt receipt = (TransactionReceipt)result;
					if(!"0x0".equals(receipt.getStatus()))
					{
						System.out.println("Call failed.");
						System.out.println();
						return;
					}
				}
        String returnObject =
                ContractClassFactory.getReturnObject(contractClass, funcName, parameterType, result);
        if (returnObject == null) {
            HelpInfo.promptNoFunc(params[1], funcName, params.length - 4);
            return;
        }
        System.out.println(returnObject);
        System.out.println();
    }  
}
