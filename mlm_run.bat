@echo off
REM Loop from 0 to 9
for /L %%i in (0,1,9) do (
    echo Running iteration %%i...
    mvn exec:java -D"exec.mainClass"="minicpbp.examples.mlm_CollieSent1_words" -D"exec.args"="1.2 5000 output 20 %%i"
)
pause

