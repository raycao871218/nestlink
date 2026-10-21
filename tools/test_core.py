#!/usr/bin/env python3
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
classes=['EthernetBridge','ConnectionSettings','MoonSettings','BootstrapPolicy','ConnectionHealth','QrPayload']
sources=[root/'app/src/main/java/com/naslink/android'/f'{name}.java' for name in classes]
tests=sorted((root/'tests').glob('*Test.java'))
with tempfile.TemporaryDirectory(prefix='harborlink-tests-') as output:
    subprocess.run(['javac','-encoding','UTF-8','-d',output,*map(str,sources),*map(str,tests)],check=True)
    for test in tests:
        args=['java','-cp',output,test.stem]
        if test.stem=='BootstrapPolicyTest': args.append(str(root/'app/src/main/assets/bootstrap/planet'))
        subprocess.run(args,check=True)
