# qaf-reportportal-integration
QMetry Automation Framework reportportal.io integration

This project provide implementation for integration of qaf test results with [reportportal.io](http://reportportal.io). 
Refer [reportportal](http://reportportal.io/docs/What-is-ReportPortal?) documentation for [installation-steps](http://reportportal.io/docs/Installation-steps).
Once you have report portal up and running you can start sending results by using `ReportPortalQAFListener` listener. 

## Features 
 * Each step as log (pass - Info, Fail - Error, Warn - Warn)
 * Web driver and element command log (DEBUG level)
 * Screen shot attachement
 * If loging enabled, logs will attached as attachment.
 
## Dependencies

Add to `POM.xml`

**dependency**

```xml

<dependency>
  <groupId>com.qmetry</groupId>
  <artifactId>qaf-reportportal-integration</artifactId>
  <version>0.0.1</version>
</dependency>

```

### Listener parameters
Description of listeners input parameters and how to configure it see “Parameters” in [Configuration section](http://reportportal.io/docs/JVM-based-clients-configuration).
Which are common for all **JVM based** agents. You can provide it in `reportportal.properties` available in class path or provide as system property.
If you want to provide it through application properties file add `system` prefix. For example:

**application.properties:**
```
system.rp.endpoint = http://localhost:8080
system.rp.uuid = <UUID of user>
system.rp.launch = default_TEST_EXAMPLE
system.rp.project = default_project
```

**Additional properties:**
rp.step.astest : boolean to specify create a node for step under test or not. If `rp.step.astest` is set to true you will see test node for each step under actual test case. Well suited for BDD implementation.

**Log4J configuration**
Add following properties in log4j.properties file:

```
log4j.rootCategory=DEBUG, CONSOLE, LOGFILE,reportportal


log4j.appender.reportportal=com.qmetry.qaf.reportportal.QAFLogAppender
log4j.appender.reportportal.layout=org.apache.log4j.PatternLayout
log4j.appender.reportportal.layout.ConversionPattern=[%d{HH:mm:ss}] %-5p (%F:%L) - %m%n

```

### Listener class:
`com.qmetry.qaf.reportportal.ReportPortalQAFListener`

There are several ways how to install listener:

- [Maven Surefire plugin](https://github.com/reportportal/agent-java-testNG#maven-surefire-plugin)
- [Specify listener in testng.xml](https://github.com/reportportal/agent-java-testNG#specify-listener-in-testngxml)
- [Custom runner](https://github.com/reportportal/agent-java-testNG#custom-runner)
- [Using command line](https://github.com/reportportal/agent-java-testNG#using-command-line)
- [Using \@Listeners annotation](https://github.com/reportportal/agent-java-testNG#using-listeners-annotation)
- [Using ServiceLoader](https://github.com/reportportal/agent-java-testNG#using-serviceloader)

> Please note, that listener must be configured in a single place only.
> Configuring multiple listeners will lead to incorrect application behavior.
