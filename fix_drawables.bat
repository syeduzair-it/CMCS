@echo off
if exist "app\src\main\res\drawable\ic_attach.xml" (
    del "app\src\main\res\drawable\ic_attach.xml"
    echo Deleted ic_attach.xml
) else (
    echo ic_attach.xml already gone
)
if exist "app\src\main\res\drawable\ic_edit.xml" (
    del "app\src\main\res\drawable\ic_edit.xml"
    echo Deleted ic_edit.xml
) else (
    echo ic_edit.xml already gone
)
echo Cleanup done
