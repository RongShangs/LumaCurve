"""Run small native host tests with MSYS on Windows or GCC in WSL."""
from pathlib import Path
import os
import re
import subprocess

MSYS_GCC=Path('C:/msys64/mingw64/bin/gcc.exe')

def _wsl_path(value):
    path=str(value)
    match=re.match(r'^([A-Za-z]):[\\/](.*)$',path)
    if not match: return path
    return '/mnt/'+match.group(1).lower()+'/'+match.group(2).replace('\\','/')

def compile_and_run(command,executable):
    if os.name!='nt':
        subprocess.run(['gcc',*command[1:]],check=True)
        return subprocess.run([str(executable)],check=True,capture_output=True,text=True)
    if MSYS_GCC.is_file():
        subprocess.run(command,check=True)
        return subprocess.run([str(executable)],check=True,capture_output=True,text=True)
    translated=[_wsl_path(arg) for arg in command[1:]]
    subprocess.run(['wsl.exe','--exec','gcc',*translated],check=True)
    return subprocess.run(['wsl.exe','--exec',_wsl_path(executable)],check=True,capture_output=True,text=True)
