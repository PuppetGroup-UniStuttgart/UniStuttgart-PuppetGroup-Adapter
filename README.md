# UniStuttgart-PuppetGroup-Adapter
Contains the java project of the JSON-RPC adapter

This Adapter generically adopts to any given gRPC API by parsing the protobuf file, and dynamically instantiating the artifacts required to invoke the target gRPC API.
Technology used for implementation is Java, with runtime typing of classes achieved using the Reflection API. It is exposed as a Java Servlet.
Interface for interaction with the Adapter is the JSON RPC protocol, with requests being accepted as HTTP requests.
