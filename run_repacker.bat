@echo off
set /p in=Mod folder name: 
set /p out=Repack folder name: 
java -jar HDSpritesRepacker.jar "%in%" "%out%" 
pause