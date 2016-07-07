/*package cloudlab.genericadapter;

import cloudlab.protoparser.ProtoParser;
import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * Created by shreyasbr on 07-07-2016.
 */
/*public class ReadmeGenerator extends HttpServlet {
    Adapter adapter;
    @Override
    public void init() throws ServletException {
        adapter = new Adapter();
        adapter.init();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        HashMap<String, List<Descriptors.FieldDescriptor>> methodRequestParameters = new HashMap<>();
        HashMap<String, String> parsedMap = ProtoParser.parse();
        String methodNames = parsedMap.get("methodNames");
        Object stub = adapter.getStub(parsedMap.get("serviceName"));
        Object blockingStub = adapter.getBlockingStub(parsedMap.get("serviceName"));
        List<Descriptors.FieldDescriptor> fieldDescriptors = new ArrayList<>();
        for(String methodToInvokeName : methodNames.split(",")) {
            System.out.println("Checking for: " + methodToInvokeName);
            Method methodToInvoke = adapter.getMethodToInvoke(stub, methodToInvokeName);
            Class parameterClass = adapter.getRequestClass(methodToInvoke);
            ParameterizedType pType = adapter.getParameterizedType(methodToInvoke);

            if (pType != null && pType.getRawType().getTypeName().equals(StreamObserver.class.getName())) { // indicates bidirectional or client side streaming
                ParameterizedType returnTypeParameterized = (ParameterizedType) methodToInvoke.getGenericReturnType();
                Class requestClass = null;
                try {
                    requestClass = Class.forName(returnTypeParameterized.getActualTypeArguments()[0].getTypeName());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                Object builderObject = adapter.getRequestBuilderObject(requestClass);
                Descriptors.Descriptor descriptorObject = adapter.getDescriptorObject(builderObject);
                fieldDescriptors = descriptorObject.getFields();
            } else {
                Object builderObject = adapter.getRequestBuilderObject(parameterClass);

                Descriptors.Descriptor descriptorObject = adapter.getDescriptorObject(builderObject);
                fieldDescriptors = descriptorObject.getFields();
            }
            methodRequestParameters.put(methodToInvokeName, fieldDescriptors);
        }

        for(String methodName : methodRequestParameters.keySet()) {
            out.println("<html><body>");
            out.println("<h2>" + methodName + "</h2>");
            out.println("<h3>Request Parameters</h3>");
            out.println("<ol>");
            List<Descriptors.FieldDescriptor> descriptors = methodRequestParameters.get(methodName);
            for(Descriptors.FieldDescriptor d : descriptors) {
                out.println("<li>" + d.getName() + "</li>");
            }
            out.println("</ol>");
            out.println("<hr>");
            out.println("</html></body>");
        }
    }
}*/
