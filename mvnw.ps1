$root = $PSScriptRoot
$jar = Join-Path $root ".mvn\wrapper\maven-wrapper.jar"
& java -classpath $jar "-Dmaven.multiModuleProjectDirectory=$root" org.apache.maven.wrapper.MavenWrapperMain @args
