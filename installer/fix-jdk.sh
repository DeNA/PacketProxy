#! /bin/sh

# bundle JDK modification script
#
# The original javapackager bundles JRE in installer.
# By this modification, the javapackager can be bundle JDK rather than JRE.

java_home=$(cd $(dirname $1) && pwd)/$(basename $1)
javapackager=${java_home}/lib/ant-javafx.jar
tempdir=`mktemp -d`

echo $1
echo $java_home

cd $tempdir
jar xf $javapackager
perl -p -e 's#home/lib#home/man#' com/oracle/tools/packager/mac/MacAppBundler.class > replaced.class
mv replaced.class com/oracle/tools/packager/mac/MacAppBundler.class
mkdir classes
mv com jdk resources classes
jar cfm ant-javafx.jar META-INF/MANIFEST.MF -C classes .
mv ant-javafx.jar $javapackager

