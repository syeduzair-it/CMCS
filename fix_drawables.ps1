$drawable = "c:\Syed Uzair\anti uz\CMCS\app\src\main\res\drawable"
$files = @("ic_attach.xml", "ic_edit.xml")
foreach ($f in $files) {
    $path = Join-Path $drawable $f
    if (Test-Path $path) {
        Remove-Item -Force $path
        Write-Output "Deleted: $f"
    } else {
        Write-Output "Already gone: $f"
    }
}
Write-Output "Done"
