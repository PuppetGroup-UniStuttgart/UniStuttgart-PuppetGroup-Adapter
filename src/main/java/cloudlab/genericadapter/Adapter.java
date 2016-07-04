package cloudlab.genericadapter;

import cloudlab.protoparser.ProtoParser;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Descriptors;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
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
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        Map<String, Object> paramsMap = reqIn.getNamedParams();
        String methodToInvokeName = reqIn.getMethod();
        JSONArray requestParameters = (JSONArray) paramsMap.get("requestParameters");
        String serviceName = (String) paramsMap.get("serviceName");
        logger.log(Level.INFO, "requestParameters.toString() = " + requestParameters.toString());


        Object stub = getStub(serviceName);

        Method methodToInvoke = getMethodToInvoke(stub, methodToInvokeName);
        if (methodToInvoke == null) {
            logger.log(Level.SEVERE, "Method Name Wrong!!!");
            out.println("WRONG METHOD NAME!!!");
        }

        Class parameterClass = getRequestClass(methodToInvoke);
        ParameterizedType pType = getParameterizedType(methodToInvoke);

        if(pType == null) { // indicates non streaming case
            stub = getBlockingStub(serviceName);
            methodToInvoke = getMethodToInvoke(stub, methodToInvokeName);
            logger.log(Level.INFO, "Reinitializing stub to blocking stub, and corresponding method for non streaming case");
            logger.log(Level.INFO, "Stub class: " + stub.getClass().getName());
        }

        if (pType != null && pType.getRawType().getTypeName().equals(StreamObserver.class.getName())) { // indicates bidirectional or client side streaming
            Class responseClass = getResponseClass(pType);
            ParameterizedType returnTypeParameterized = (ParameterizedType) methodToInvoke.getGenericReturnType();
            logger.log(Level.INFO, "returnTypeParameterized.getActualTypeArguments()[0].getTypeName() = " + returnTypeParameterized.getActualTypeArguments()[0].getTypeName());
            Class requestClass = null;
            try {
                requestClass = Class.forName(returnTypeParameterized.getActualTypeArguments()[0].getTypeName());
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            logger.log(Level.INFO, "requestClass = " + requestClass);
            Object builderObject = getRequestBuilderObject(requestClass);
            Descriptors.Descriptor descriptorObject = getDescriptorObject(builderObject);
            List<Descriptors.FieldDescriptor> fieldDescriptors = descriptorObject.getFields();

            // instantiate a stream observer for response class. and add functionality to sysout output
            ParameterizedType finalPType = pType;
            StringBuilder responseStringBuilder = new StringBuilder();
            final SettableFuture<Void> finishFuture = SettableFuture.create();
            StreamObserver responseObserver = new StreamObserver() {
                Class responseClass = getResponseClass(finalPType);
                int index = 0;

                @Override
                public void onNext(Object value) {
                    logger.log(Level.INFO, "onNext of response observer called");

                    String fieldGetterName = getFieldGetterName(fieldDescriptors, index);
                    try {
                        Method getOutputMethod = responseClass.getDeclaredMethod(fieldGetterName);
                        logger.log(Level.INFO, "Value returned by getter " + getOutputMethod.getName() + " : " + getOutputMethod.invoke(value));
                        responseStringBuilder.append(getOutputMethod.invoke(value)).append("\n");
                        index++;
                    } catch (NoSuchMethodException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    } catch (InvocationTargetException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    } catch (IllegalAccessException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "Exception in response stream", t);
                    finishFuture.setException(t);
                }

                @Override
                public void onCompleted() {
                    logger.log(Level.INFO, "Response stream completed");
                    finishFuture.set(null);
                }
            };

            // pass it to methodToInvoke while invoking it on stub object
            StreamObserver requestObserver = null;
            try {
                requestObserver = (StreamObserver) methodToInvoke.invoke(stub, responseObserver);
            } catch (IllegalAccessException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            } catch (InvocationTargetException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }


            int index = 0;
            for (Descriptors.FieldDescriptor f : fieldDescriptors) {
                String methodName = getFieldSetterMethodName(f);

                logger.log(Level.INFO, "methodName = " + methodName);
                Method setMethod = null;
                try {
                    setMethod = builderObject.getClass().getDeclaredMethod(methodName, ProtoParser.getJavaClass(f.getJavaType().toString()));
                } catch (NoSuchMethodException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
                try {
                    builderObject = setMethod.invoke(builderObject, ProtoParser.getWrapperObject(requestParameters.get(index), f.getJavaType().toString()));
                } catch (IllegalAccessException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                } catch (InvocationTargetException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }

                logger.log(Level.INFO, "setMethod.getName() = " + setMethod.getName());
                logger.log(Level.INFO, "Setting: " + requestParameters.get(index));

                Method buildMethod = null;
                try {
                    buildMethod = builderObject.getClass().getDeclaredMethod("build", null);
                } catch (NoSuchMethodException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
                Object requestObject = null;
                try {
                    requestObject = buildMethod.invoke(builderObject, null);
                } catch (IllegalAccessException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                } catch (InvocationTargetException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
                logger.log(Level.INFO, "requestObject.getClass() = " + requestObject.getClass());

                // pass requestObject to onNext of requestObserver
                requestObserver.onNext(requestObject);
                index++;
            }
            requestObserver.onCompleted();

            logger.log(Level.INFO, "Before future loop: " + finishFuture.isDone());
            while (!finishFuture.isDone()) {

            }
            logger.log(Level.INFO, "after future loop");
            try {
                finishFuture.get();
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            } catch (ExecutionException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }

            JSONRPC2Response rpcResponse = new JSONRPC2Response(responseStringBuilder.toString(), reqIn.getID());
            out.println(rpcResponse.toString());
        } else {
            Object builderObject = getRequestBuilderObject(parameterClass);

            Descriptors.Descriptor descriptorObject = getDescriptorObject(builderObject);
            List<Descriptors.FieldDescriptor> fieldDescriptors = descriptorObject.getFields();
            int index = 0;
            for (Descriptors.FieldDescriptor f : fieldDescriptors) {
                String methodName = getFieldSetterMethodName(f);
                logger.log(Level.INFO, "methodName = " + methodName);
                Method setMethod;
                try {
                    setMethod = builderObject.getClass().getDeclaredMethod(methodName, ProtoParser.getJavaClass(f.getJavaType().toString()));
                    builderObject = setMethod.invoke(builderObject, ProtoParser.getWrapperObject(requestParameters.get(index), f.getJavaType().toString()));
                    logger.log(Level.INFO, "setMethod.getName() = " + setMethod.getName());
                    logger.log(Level.INFO, "Setting value: " + requestParameters.get(index));
                    logger.log(Level.INFO, "Wrapper class: " + ProtoParser.getWrapperObject(requestParameters.get(index), f.getJavaType().toString()).getClass());
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
                logger.log(Level.INFO, "requestObject.getClass() = " + requestObject.getClass());
            } catch (NoSuchMethodException e) {
                logger.log(Level.WARNING, "No such method build", e);
            } catch (InvocationTargetException e) {
                logger.log(Level.WARNING, "Cannot invoke method build", e);
            } catch (IllegalAccessException e) {
                logger.log(Level.WARNING, "Cannot access method build", e);
            }

            Object replyObject = null;
            try {
                replyObject = methodToInvoke.invoke(stub, requestObject);
                logger.log(Level.INFO, "replyObject.getClass() = " + replyObject.getClass());
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
    }

    private String getFieldGetterName(List<Descriptors.FieldDescriptor> fieldDescriptors, int index) {
        Descriptors.FieldDescriptor f = fieldDescriptors.get(index);
        char first = Character.toUpperCase(f.getName().charAt(0));
        return "get" + first + f.getName().substring(1);
    }

    private String getFieldSetterMethodName(Descriptors.FieldDescriptor f) {
        String methodName;
        char first = Character.toUpperCase(f.getName().charAt(0));
        if (f.isRepeated()) {
            methodName = "add" + first + f.getName().substring(1);
        } else if (f.isMapField()) {
            methodName = "putAll" + first + f.getName().substring(1);
        } else {
            methodName = "set" + first + f.getName().substring(1);
            if (f.getJavaType().toString().toLowerCase().equals("enum")) { // set<FieldName>Value for Enum
                methodName += "Value";
            }
        }
        return methodName;
    }

    private Object getBlockingStub(String serviceName) {
        HashMap<String, String> parsedMap = ProtoParser.parse();
        Class cls;
        Object blockingStubObject = null;
        try {
            cls = Class.forName(parsedMap.get("packageName") + "." + serviceName + "Grpc");
            logger.log(Level.INFO, "Grpc class name: " + cls.getName());
            Method getStubMethod = cls.getDeclaredMethod("newBlockingStub", Channel.class);
            logger.log(Level.INFO, "getStubMethod = " + getStubMethod);
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
        logger.log(Level.INFO, "blockingStubObject.getClass() = " + blockingStubObject.getClass());
        logger.log(Level.INFO, "blockingStubObject.getClass().getTypeName() = " + blockingStubObject.getClass().getTypeName());
        return blockingStubObject;
    }

    private Object getStub(String serviceName) {
        HashMap<String, String> parsedMap = ProtoParser.parse();
        Class cls;
        Object stubObject = null;
        try {
            cls = Class.forName(parsedMap.get("packageName") + "." + serviceName + "Grpc");
            logger.log(Level.INFO, "Grpc class name: " + cls.getName());
            Method getStubMethod = cls.getDeclaredMethod("newStub", Channel.class);
            logger.log(Level.INFO, "getStubMethod = " + getStubMethod);
            stubObject = getStubMethod.invoke(null, channel);
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "Blocking Stub class not found", e);
        } catch (NoSuchMethodException e) {
            logger.log(Level.WARNING, "Unable to get newBlockingStub method", e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Unable to access method newBlockingStub", e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, "Unable to invoke method newBlockingStub", e);
        }
        logger.log(Level.INFO, "stubObject.getClass() = " + stubObject.getClass());
        logger.log(Level.INFO, "stubObject.getClass().getTypeName() = " + stubObject.getClass().getTypeName());
        return stubObject;
    }

    private Method getMethodToInvoke(Object blockingStub, String methodToInvokeName) {
        Method methodToInvoke = null;
        Method[] methods = blockingStub.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodToInvokeName)) {
                methodToInvoke = method;
            }
        }
        logger.log(Level.INFO, "methodToInvoke = " + methodToInvoke.getName());
        return methodToInvoke;
    }

    private Class getRequestClass(Method methodToInvoke) {
        Class requestClass = null;
        Parameter[] parametersList = methodToInvoke.getParameters();
        requestClass = parametersList[0].getType();
        logger.log(Level.INFO, "Parameter type: " + requestClass);
        return requestClass;
    }

    private Class getResponseClass(ParameterizedType pType) {
        Class responseClass = null;
        try {
            responseClass = Class.forName(pType.getActualTypeArguments()[0].getTypeName());
            logger.log(Level.INFO, "responseClass = " + responseClass);
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return responseClass;
    }

    private ParameterizedType getParameterizedType(Method methodToInvoke) {
        ParameterizedType pType = null;
        Parameter[] parametersList = methodToInvoke.getParameters();
        try {
            pType = (ParameterizedType) parametersList[0].getParameterizedType();
            logger.log(Level.INFO, "Parameterized Type: " + parametersList[0].getParameterizedType());
        } catch (ClassCastException e) {
            logger.log(Level.INFO, "Non-streaming case. Not casting to ParameterizedType", e);
        }
        return pType;
    }

    private Object getRequestBuilderObject(Class requestClass) {
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
            logger.log(Level.WARNING, e.getMessage(), e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        } catch (InvocationTargetException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return descriptorObject;
    }
}
