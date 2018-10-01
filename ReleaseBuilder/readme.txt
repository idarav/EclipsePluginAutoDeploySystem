TO COMPILE: javac ./ReleaseBuilder.java
TO RUN:     java -cp . ReleaseBuilder

Auto eclipse deploy system steps:

for the TEMPLATE:
in eclipse.ini set:
-Dosgi.instance.area.default=./eclipse-workspace
-Dorg.osgi.framework.bundle.parent=ext
-Dosgi.framework.extensions=org.eclipse.wst.jsdt.nashorn.extension

add projects to the eclipse-workspace dir
import these projects
to set XM server's address put
    KNOWLEDGEBASE_SERVER_ADDRESS=http\://10.6.13.58
    eclipse.preferences.version=1
into
    "\eclipse-workspace\.metadata\.plugins\org.eclipse.core.runtime\.settings\org.eclipse.scava.plugin.prefs"
disable welcome screen: https://stackoverflow.com/a/52254063


for the INSTANCE:
create a copy of the template
copy plugin from "org.eclipse.scava.root\releng\org.eclipse.scava.update\target\repository\plugins" to plugins dir

for AUTO-PROJECT IMPORT:
copy plugin "com.seeq.eclipse.importprojects_1.4.0.jar"
probably let it run once (mb headless?)
execute the following: be careful with the absolute path after the import argument
    .\eclipsec.exe -nosplash -application com.seeq.eclipse.importprojects.headlessimport -data "./eclipse-workspace" -import "D:\_Projects\Work\crossminer\testEnvironment\eclipse\eclipse-workspace"
remove "com.seeq.eclipse.importprojects_1.4.0.jar"

SUMMARY based on my experiences:
- unzip
- edit eclipse.ini
- install CROSSMINER plugin
- create ./eclipse-workspace dir
- create org.eclipse.scava.plugin.prefs with the correct values
- PROBABLY: disable welcome screen
- install PROJECT IMPORTER plugin
- copy projects into ./eclipse-workspace
- PROBABLY: run once to get installed every plugin? got some exceptions last time
- execute import command
- delete PROJECT IMPORTER plugin
- zip


SO THE SCRIPT:
parameters:
-path to plugin's .jar

