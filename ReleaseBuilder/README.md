# ReleaseBuilder
###### created by Endre Váradi

## What is this
This is a tool created to help our Eclipse plug-in development in testing by easily creating Eclipse packages (works with Windows and Linux distributions too) with our plug-in preinstalled from the built *updatesite* into them. It can also import prepared *test projects* into the workspace, whcich can also be prepared for the tests, like putting there some *Preferences* files.
The *eclipse.ini* also can be edited to prepare for the tests.

## How to use it
### compile: 
`javac ./ReleaseBuilder.java`
### run:
`java -cp . ReleaseBuilder`
#### To see the available launch options, use the -h launch parameter
### Steps it takes
- grab an Eclipse distribution from the **eclipses/** dir
- copy into the **tmp/** temporary folder
- edit **eclipse.ini**
- install **Project importer plugin**
- prepare **eclipse-workspace/** dir
- copy projects from **projects/** dir into **eclipse-workspace/** dir
- two headless run, just to finish the installation
- install plugins from the give updatesite
- copy the given document
- zip the prepared Eclipse into **releases/\<version\>/** dir