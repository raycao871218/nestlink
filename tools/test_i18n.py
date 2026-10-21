#!/usr/bin/env python3
import pathlib,re,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/res'
a=ET.parse(root/'values/strings.xml').getroot()
b=ET.parse(root/'values-zh-rCN/strings.xml').getroot()
def strings(tree):return {x.attrib['name']:''.join(x.itertext()) for x in tree.findall('string')}
en,zh=strings(a),strings(b)
assert en.keys()==zh.keys(), 'Translation keys differ'
for key in en:
    assert en[key] and zh[key], key
    assert sorted(re.findall(r'%[0-9]+\$[sd]',en[key]))==sorted(re.findall(r'%[0-9]+\$[sd]',zh[key])), key
assert en['app_name']=='NestLink' and zh['app_name']=='窝窝连'
for tree in (a,b):
    plural=tree.find("plurals[@name='moon_config_count']")
    assert plural is not None
    assert {x.attrib['quantity'] for x in plural}=={'one','other'}
print('PASS English/Chinese key parity, interpolation placeholders and Moon plurals')
