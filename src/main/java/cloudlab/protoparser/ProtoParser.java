package cloudlab.protoparser;

import cloudlab.genericadapter.Adapter;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by shreyasbr on 27-05-2016.
 */
public class ProtoParser {
    private static final Logger logger = Logger.getLogger(ProtoParser.class.getName());

    public static HashMap<String, String> parse() {
        HashMap<String, String> parsedMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(new File("/api/main.proto")))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("package")) {
                    String packageName = line.split(" ")[1].substring(0, line.split(" ")[1].length() - 1);
                    System.out.println("packageName = " + packageName);
                    parsedMap.put("packageName", packageName);
                } else if (line.startsWith("service")) {
                    String serviceName = line.split(" ")[1];
                    System.out.println("serviceName = " + serviceName);
                    parsedMap.put("serviceName", serviceName);
                }
            }
        } catch (FileNotFoundException e) {
            logger.log(Level.WARNING, "Proto file not found", e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error while parsing Proto file", e);
        }
        System.out.println("parsedMap = " + parsedMap);
        return parsedMap;
    }

    public static Class getJavaClass(String javaType) {
        System.out.println("javaType = " + javaType);
        if (javaType.toLowerCase().equals("string")) {
            return String.class;
        } else if (javaType.toLowerCase().equals("float")) {
            return float.class;
        } else if (javaType.toLowerCase().equals("int")) {
            return int.class;
        } else if (javaType.toLowerCase().equals("long")) {
            return long.class;
        } else if (javaType.toLowerCase().equals("double")) {
            return double.class;
        } else if (javaType.toLowerCase().equals("boolean")) {
            return boolean.class;
        } else if (javaType.toLowerCase().equals("enum")) {
            return int.class;
        } else if(javaType.toLowerCase().equals("message")) {
            return Map.class;
        }
        System.out.println("No equivalent Java Type found for: " + javaType);
        return null;
    }

    public static Object getWrapperObject(Object paramString, String javaType) {
        if (javaType.toLowerCase().equals("string")) {
            return paramString;
        } else if (javaType.toLowerCase().equals("float")) {
            try {
                Class floatClass = Class.forName("java.lang.Float");
                String methodName = "parseFloat";
                Method parseMethod = floatClass.getDeclaredMethod(methodName, String.class);
                return parseMethod.invoke(null, paramString);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (javaType.toLowerCase().equals("int") || javaType.toLowerCase().equals("enum")) {
            try {
                Class intClass = Class.forName("java.lang.Integer");
                String methodName = "parseInt";
                Method parseMethod = intClass.getDeclaredMethod(methodName, String.class);
                return parseMethod.invoke(null, paramString);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (javaType.toLowerCase().equals("double")) {
            try {
                Class doubleClass = Class.forName("java.lang.Double");
                String methodName = "parseDouble";
                Method parseMethod = doubleClass.getDeclaredMethod(methodName, String.class);
                return parseMethod.invoke(null, paramString);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (javaType.toLowerCase().equals("long")) {
            try {
                Class longClass = Class.forName("java.lang.Long");
                String methodName = "parseLong";
                Method parseMethod = longClass.getDeclaredMethod(methodName, String.class);
                return parseMethod.invoke(null, paramString);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if (javaType.toLowerCase().equals("boolean")) {
            try {
                Class boolClass = Class.forName("java.lang.Boolean");
                String methodName = "parseBoolean";
                Method parseMethod = boolClass.getDeclaredMethod(methodName, String.class);
                return parseMethod.invoke(null, paramString);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else if(javaType.toLowerCase().equals("message")) {
            return (Map)paramString;
        }
        return null;
    }
}
