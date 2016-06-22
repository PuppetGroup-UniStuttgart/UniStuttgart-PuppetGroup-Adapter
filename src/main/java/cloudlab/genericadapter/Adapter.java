package cloudlab.genericadapter;

import cloudlab.protoparser.ProtoParser;
import com.google.protobuf.Descriptors;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.minidev.json.JSONArray;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by shreyasbr on 25-05-2016.
 */
public class Adapter extends HttpServlet {
    private static final Logger logger = Logger.getLogger(Adapter.class.getName());
    private ManagedChannel channel;

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Override
    public void init() throws ServletException {
        Map<String, String> env = System.getenv();
        channel = ManagedChannelBuilder.forAddress(env.get("API_HOST"), Integer.parseInt(env.get("API_PORT"))).usePlaintext(true).build();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();

        String jsonString = req.getParameter("jsonString");
        JSONRPC2Request reqIn = null;
        try {
            reqIn = JSONRPC2Request.parse(jsonString);
        } catch (JSONRPC2ParseException e) {
            e.printStackTrace();
        }
        Map<String, Object> paramsMap = reqIn.getNamedParams();
        String methodToInvokeName = reqIn.getMethod();
        JSONArray requestParameters = (JSONArray) paramsMap.get("requestParameters");
        String serviceName = (String) paramsMap.get("serviceName");
        System.out.println("requestParameters.toString() = " + requestParameters.toString());

        Object blockingStub = getBlockingStub(serviceName);

        Method methodToInvoke = getMethodToInvoke(blockingStub, methodToInvokeName);
        if (methodToInvoke == null) {
            logger.log(Level.SEVERE, "Method Name Wrong!!!");
            out.println("WRONG METHOD NAME!!!");
        }

        Class requestClass = getRequestClass(methodToInvoke);
        Object builderObject = getBuilderObject(requestClass);

        Descriptors.Descriptor descriptorObject = getDescriptorObject(builderObject);
        List<Descriptors.FieldDescriptor> fieldDescriptors = descriptorObject.getFields();
        int index = 0;
        for (Descriptors.FieldDescriptor f : fieldDescriptors) {
            char first = Character.toUpperCase(f.getName().charAt(0));

            String methodName;
            if (f.isRepeated()) {
                methodName = "add" + first + f.getName().substring(1);
            } else {
                methodName = "set" + first + f.getName().substring(1);
                if (f.getJavaType().toString().toLowerCase().equals("enum")) {
                    methodName += "Value";
                }
            }
            System.out.println("methodName = " + methodName);
            Method setMethod;
            try {
                setMethod = builderObject.getClass().getDeclaredMethod(methodName, ProtoParser.getJavaClass(f.getJavaType().toString()));
                builderObject = setMethod.invoke(builderObject, ProtoParser.getWrapperObject(requestParameters.get(index), f.getJavaType().toString()));
                System.out.println("setMethod.getName() = " + setMethod.getName());
                System.out.println("Setting value: " + requestParameters.get(index));
                System.out.println("Wrapper class: " + ProtoParser.getWrapperObject(requestParameters.get(index), f.getJavaType().toString()).getClass());
                index++;
            } catch (NoSuchMethodException e) {
                logger.log(Level.WARNING, "No such method " + methodName, e);
            } catch (InvocationTargetException e) {
                logger.log(Level.WARNING, "Cannot invoke method " + methodName, e);
            } catch (IllegalAccessException e) {
                logger.log(Level.WARNING, "Cannot access method " + methodName, e);
            }
        }

        Method buildMethod;
        Object requestObject = null;
        try {
            buildMethod = builderObject.getClass().getDeclaredMethod("build", null);
            requestObject = buildMethod.invoke(builderObject, null);
            System.out.println("requestObject.getClass() = " + requestObject.getClass());
        } catch (NoSuchMethodException e) {
            logger.log(Level.WARNING, "No such method build", e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, "Cannot invoke method build", e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Cannot access method build", e);
        }

        Object replyObject = null;
        try {
            replyObject = methodToInvoke.invoke(blockingStub, requestObject);
            System.out.println("replyObject.getClass() = " + replyObject.getClass());
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Cannot access method " + methodToInvokeName, e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, "Cannot invoke method " + methodToInvokeName, e);
        }

        Method getAllFieldsMethod;
        StringBuilder response = new StringBuilder();
        try {
            getAllFieldsMethod = replyObject.getClass().getSuperclass().getDeclaredMethod("getAllFields");
            Map<Descriptors.FieldDescriptor, Object> outputMap = (Map<Descriptors.FieldDescriptor, Object>) getAllFieldsMethod.invoke(replyObject, null);
            for (Descriptors.FieldDescriptor fieldDescriptor : outputMap.keySet()) {
                response.append(outputMap.get(fieldDescriptor).toString()).append("\n");
            }
        } catch (NoSuchMethodException e) {
            logger.log(Level.WARNING, "No such method getOutput", e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, "Cannot invoke method getOutput", e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Cannot access method getOutput", e);
        }

        JSONRPC2Response rpcResponse = new JSONRPC2Response(response.toString(), reqIn.getID());
        out.println(rpcResponse.toString());
    }

    private Object getBlockingStub(String serviceName) {
        HashMap<String, String> parsedMap = ProtoParser.parse();
        Class cls;
        Object blockingStubObject = null;
        try {
            cls = Class.forName(parsedMap.get("packageName") + "." + serviceName + "Grpc");
            System.out.println(cls.getName());
            /*Class[] p = new Class[1];
            p[0] = Channel.class;*/
            Method getStubMethod = cls.getDeclaredMethod("newBlockingStub", Channel.class);
            System.out.println("getStubMethod = " + getStubMethod);
            blockingStubObject = getStubMethod.invoke(null, channel);
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "Blocking Stub class not found", e);
        } catch (NoSuchMethodException e) {
            logger.log(Level.WARNING, "Unable to get newBlockingStub method", e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Unable to access method newBlockingStub", e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, "Unable to invoke method newBlockingStub", e);
        }
        System.out.println("blockingStubObject.getClass() = " + blockingStubObject.getClass());
        System.out.println("blockingStubObject.getClass().getTypeName() = " + blockingStubObject.getClass().getTypeName());
        return blockingStubObject;
    }

    private Method getMethodToInvoke(Object blockingStub, String methodToInvokeName) {
        Method methodToInvoke = null;
        Method[] methods = blockingStub.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodToInvokeName)) {
                methodToInvoke = method;
            }
        }
        System.out.println("methodToInvoke = " + methodToInvoke.getName());
        return methodToInvoke;
    }

    private Class getRequestClass(Method methodToInvoke) {
        Class requestClass = null;
        Parameter[] parametersList = methodToInvoke.getParameters();
        for (Parameter parameter : parametersList) {
            System.out.println(parameter.getType());
            requestClass = parameter.getType();
        }
        return requestClass;
    }

    private Object getBuilderObject(Class requestClass) {
        Method builderMethod;
        Object builderObject = null;
        try {
            builderMethod = requestClass.getDeclaredMethod("newBuilder", null);
            builderObject = builderMethod.invoke(null, null);
        } catch (NoSuchMethodException e) {
            logger.log(Level.WARNING, "No such method newBuilder", e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Cannot access method newBuilder", e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, "Cannot invoke method newBuilder", e);
        }
        return builderObject;
    }

    private Descriptors.Descriptor getDescriptorObject(Object builderObject) {
        Method descriptorMethod;
        Descriptors.Descriptor descriptorObject = null;
        try {
            descriptorMethod = builderObject.getClass().getDeclaredMethod("getDescriptor", null);
            descriptorObject = (Descriptors.Descriptor) descriptorMethod.invoke(builderObject, null);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return descriptorObject;
    }
}
