#!/usr/bin/env python3
import os,pathlib,subprocess,tempfile
root=pathlib.Path(__file__).resolve().parents[1]
cache=pathlib.Path(os.environ.get('GRADLE_USER_HOME',pathlib.Path.home()/'.gradle'))
jars=list((cache/'caches/modules-2/files-2.1/com.google.zxing/core/3.5.3').glob('*/core-3.5.3.jar'))
if not jars: raise SystemExit('Build the app first to resolve ZXing core 3.5.3.')
package=root/'app/src/main/java/com/naslink/android'
sources=[package/(name+'.java') for name in ['QrDecoder','QrPayload','MoonSettings','ConnectionSettings']]
with tempfile.TemporaryDirectory(prefix='harborlink-qr-') as out:
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jars[0]),'-d',out,*map(str,sources),str(root/'tests/qr/QrDecoderTest.java')],check=True)
    subprocess.run(['java','-cp',out+os.pathsep+str(jars[0]),'QrDecoderTest'],check=True)
