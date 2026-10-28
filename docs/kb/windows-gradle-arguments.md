# Gradle properties in PowerShell

Quote dotted Gradle property arguments when invoking the wrapper from PowerShell:

```powershell
.\gradlew.bat :mod:runScenario '-Pscenario=smoke' '-Pscenario.timeoutSeconds=120'
```

An unquoted `-Pscenario.timeoutSeconds=120` was split at the dot in a native batch
invocation. Gradle reported `Task '.timeoutSeconds=120' not found` before starting
the client. Quoting the whole argument fixed task selection; the unchanged
bounded smoke then passed. This is a launch-argument error, not a failed scenario
or evidence of a missing display. Keep the original log and correct the argument
instead of changing the timeout, task, threshold or a scenario's verdict.
