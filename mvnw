#!/bin/sh
set -e
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
PRGDIR=`dirname "$PRG"`

JAVACMD="java"
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
fi

if [ ! -x "$JAVACMD" ] && ! command -v "$JAVACMD" >/dev/null 2>&1 ; then
  echo "The JAVA_HOME environment variable is not defined correctly." >&2
  exit 1
fi

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-"$PRGDIR"}"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Maven wrapper jar not found: $WRAPPER_JAR" >&2
  exit 1
fi

exec "$JAVACMD" \
  $MAVEN_OPTS \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"

