# Using Spring Cloud Functions with OpenShift Serverless

Spring Cloud Functions is yet another interesting option to be used by Java developers when building Serverless functions.  
You have already seen how to build and run applications for OpenShift Serverless using Quarkus, but on this article we are going to talk about how to use Spring Cloud Functions and walk you through the steps, which are really not that different than running any Spring Boot application with OpenShift Serverless as well. 

One of benefits of building an open hybrid serverles platform is to empower developers to have choice of programming languages, tools, frameworks and portability across any environment to run serverless applications. 

## Requirements

- Gradle 
- Java Development Tool Kit (JDK 8+)
- OpenShift 4.3+
- [OpenShift Serverless](https://openshift.com/serverless) 1.7+ 
- `curl`
- `kn` (Knative Client)

## Generate the Spring Cloud Functions project

One of the easiest ways to generate a Spring project is using `curl` to access `start.sproing.io` and that's exaclty how we will start our project.   

```shell 
curl https://start.spring.io/starter.tgz -d dependencies=web,actuator,cloud-function \
-d language=java -d type=gradle-project -d baseDir=my-function-project | tar -xzvf -
```

This will generate and download a project inside the `my-function-project` folder.

## Implement your Function

We are going to implement a quick and dirty Google Translator wrapper.  In order to create functions using Spring Cloud Functions, you need a method with the `@Bean` annotation that 
can pretty much follow any of the functional interfaces from `java.util.Function` such as `Consumer`, `Supplier` or `Function`. For more details about how Spring Cloud Functions work please check the documentation, but for this example just copy and paste the following method to your DemoApplication.java file. 

```java 
@Bean
public Function<String, String> translate() {
    return input -> {
        final String fromLang = "en";
        final String toLang = "es";
        final String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + fromLang + "&tl="
                + toLang + "&dt=t&q=" + input;

        final RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        String result = response.getBody();

        // clean up results
        int index = result.indexOf(",");
        result = result.substring(3, index).replace("\"", "");
        return result;
    };

}
```

### Test your function locally

Since Spring Cloud Functions are just Spring Boot apps, you can implement unit tests just like any other Java application, using JUnit, Mockito or whatever you would like. 

You can also run the application locally, using `gradle bootRun` or `mvn spring-boot:run`, useful to validate the application iteratively before running inside a container. 

Every function will be mapped to an endpoint that can be accesed as follows: 

```
http://localhost:8080/<functionName>
```

Concretely for our translate function you can access the endpoint using the following: 

``` shell 
$ curl http://localhost:8080/translate  -H "Content-Type: text/plain" -d "Hello"
"Hola"
```

Using `curl` we post a word and it gets translated to Spanish. You can parse the output and format it properly, but to keep this short I'll let that as an exercise for the reader.  

May [Jackson](https://github.com/FasterXML/jackson) and [Json](https://www.json.org/json-en.html) be with your friends. 

## Build a Container using Jib 

So far we have built a Spring Application and run it locally, but it's time to containarize the application.  There are many ways to execute this step, but I've decided to stick to well known tools used by the Java community, so I'll use `Jib` and add it as a plugin to my Gradle project. 

Edit the `build.gradle` file and append this line `id 'com.google.cloud.tools.jib' version "2.4.0"` to your plugins section. It should look like this: 

``` java
plugins {
	id 'org.springframework.boot' version '2.4.0-SNAPSHOT'
	id 'io.spring.dependency-management' version '1.0.9.RELEASE'
	id 'java'
	id 'com.google.cloud.tools.jib' version "2.4.0"
}
```

With Jib as part of your gradle project, Gradle can build your container and push it your container registry of choice.  Quay or DockerHub are well known choices. 

After you have the jib plugin on your gradle project, build a container for the project using the following command: 

```
gradle build jib --image=<your_container_registry>/demo-app:v1
```

This will generate a container for your project and automatically push to the container registry of choice.

## Deploy to OpenShift Serverless

With the container built you can deploy the application using `kn` - The Knative CLI.  

```bash
kn service create translator --image=<your_container_registry>/demo-app:v1 --autoscale-window 6s --revision-name=v1

Creating service 'translator' in namespace 'serverless-demo':

  0.406s The Route is still working to reflect the latest desired specification.
  0.538s Configuration "translator" is waiting for a Revision to become ready.
 14.125s ...
 14.288s Ingress has not yet been reconciled.
 14.450s Ready to serve.

Service 'translator' created to latest revision 'translator-jcdbc-1' is available at URL:
http://translator-<namespace>.apps.<clustername>.<domain>.org
``` 

OpenShift Serverless will do the heavy lifting of creating a Kubernetes deployment, a route, configure SSL (using your cluster's configuration), and auto-scale this application based on the number of requests.  For more details about OpenShift Serverless, please check the [product documentation](https://docs.openshift.com/container-platform/4.4/serverless/serverless-getting-started.html).

This will auto-scale down the application (to zero) after 6 seconds without a new request.

Try it again but now using the URL from the service running in OpenShift.

```
$ curl https://translator-<namespace>.apps.<cluster>.<domain>.org  -H "Content-Type: text/plain" -d "This is my second test"
"Esta es mi segunda prueba"
... 
```

## Adding a new Function that can handle a JSON document 

When creating REST APIs it is very common to send and receive JSON documents and you can very easily add a Function that instead of taking Strings take a JSON message instead. 

### The data model

Create a new `UserReview.java` file with the following content: 

```java
package com.example.demo; 

public class UserReview {

    private String comment;
    private int rating;
    private String username;

    public UserReview() {
        
    }

    public String getComment() {
        return comment;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
```

We will use this class to marshal and unmarshal the JSON objects being sent or received by our API.  Create a `review.json` file with the content below.  This will be the input of our new `translateReview` function.

```json 
{"comment":"This product is great","rating":5,"username":"john"}
```

### The function code

This will be very similar to the previous function we created, it's just another method with the `@Bean` annotation and using our POJO class as input and output.

```java
@Bean
public Function<UserReview, UserReview> translateReview() {
    return input -> {
        final UserReview output = input;
        output.setComment(translate().apply(input.getComment()));
        return output;
    };
}
```
### Posting a JSON file

We will continue using `curl` as our client here and specificy a path to the `review.json` file created on the previous step. 

```bash
curl http://localhost:8080/translateReview -X POST -H "Content-Type: application/json" -d @<path_to>/review.json
{"comment":"Este producto es genial","rating":5,"username":"john"}%
```
### Building your new container

Same as before but using a `v2` tag.

```bash
gradle jib --image=<your_container_registry>/demo-app:v2
```

### Updating the deployed application to include the new function

Now we are going to use another interesting feature of OpenShift Serverless, we will deploy a new version of the application but with a different URL, that way current clients of this API won't even know about this new functionality until we decide to rollout traffic to it. 

```bash
kn service update translator --image=<your_container_registry>/demo-app:v2 --traffic translator-v1=100 --tag @latest=preview
```

This can be achieved by using tags.  In this example I'm tagging a particular revision `@latest` with a `preview` value that will be essentially appended to the service URL. Also note that I'm setting 100% of traffic to the previous revision `translator-v1`, which means no traffic will be sent to the new version being deployed. This is also called dark launch, where a new version of my application is available in a production environment but not necessarily receiving any requests unless someone knows which URL to use. 

After validation is completed you can decide to gradually rollout traffic using Canary deployments or Blue/Green.  There is a step-by-step lab about these scenarios on https://learn.openshift.com/developing-on-openshift/serverless/ 

### Testing 

Now you can execute the same curl command as before but this time adding the `preview` prefix. 

```bash
curl https://preview-translator-serverless-demo.apps.ci-ln-7vhysib-d5d6b.origin-ci-int-aws.dev.rhcloud.com/translateReview -X POST -H "Content-Type: application/json" -d @<path_to>/review.json
```

## Conclusion 

You can easily build and deploy a Spring Cloud Functions application using OpenShift Serverless. The workflow feels very natural for a Java developer and you can even build the container using a Gradle (or Maven) plugin such as Jib. 

Using OpenShift Serverless you can deploy multiple versions of the application and perform a dark launch, blue/green or canary deployments with no sweat.

For more details check https://openshift.com/serverless.