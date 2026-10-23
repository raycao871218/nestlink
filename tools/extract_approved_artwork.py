from pathlib import Path
from PIL import Image,ImageDraw
import json,shutil
source=Path('design/approved-home-reference.png')
im=Image.open(source).convert('RGB')
# Mechanical extraction of approved artwork only; no redraw or generation.
regions={
'chicken':(64,36,170,151),'brand':(180,62,340,127),
'heading':(82,177,562,291),'gear':(515,56,579,126),
'power':(177,459,264,537),'book':(80,1002,155,1066),'nas':(94,865,176,936),
'ready':(418,609,473,665), 'family':(87,613,258,658),
'internet':(87,697,290,746),'available':(480,613,545,662),
'current':(88,813,211,852),'home_nas':(199,860,364,906),
'profile_hint':(200,905,500,941),'guide':(172,1003,350,1043),
'guide_hint':(172,1042,452,1077),'disconnect':(296,466,480,525),
'connect':(382,466,482,525),'chevron':(522,878,552,933),'connected':(294,322,438,384),'connected_badge':(185,299,460,401)
}
android=Path('app/src/main/res/drawable-nodpi')
ios=Path('ios/App/Assets.xcassets')
def save(name,pic):
 pic.save(android/f'approved_{name}.png')
 folder=ios/f'Approved{name.title().replace("_","")}.imageset';folder.mkdir(exist_ok=True)
 pic.save(folder/'art.png');(folder/'Contents.json').write_text(json.dumps({'images':[{'filename':'art.png','idiom':'universal'}],'info':{'author':'xcode','version':1}},indent=2)+'\n',encoding='utf-8')
for name,rect in regions.items():save(name,im.crop(rect))
# Stretchable controls retain the original border pixels. Remove only the static
# interior screenshot content, using blank paper from the same approved image.
rects={'panel':(56,576,587,776),'button':(56,416,589,563),'frame':(23,11,619,1128)}
paper=im.crop((320,602,390,653))
yellow=im.crop((98,445,151,478))
for name,rect in rects.items():
 pic=im.crop(rect);w,h=pic.size;inset=22 if name!='frame' else 30
 tile=yellow if name=='button' else paper
 # Native nine-slice rendering never uses the screenshot's old text in the center.
 # Fill all interior pixels before packaging resizable panels.
 for y in range(inset,h-inset,tile.height):
  for x in range(inset,w-inset,tile.width):
   tw=min(tile.width,w-inset-x);th=min(tile.height,h-inset-y);pic.paste(tile.crop((0,0,tw,th)),(x,y))
 save(name,pic)
# Header and app icon both use this exact short, round chicken from the reference.
chicken=im.crop(regions['chicken'])
chicken.save(android/'chicken_badge.png')
chicken.save(ios/'ChickenBadge.imageset/chicken.png')
icon=Image.new('RGB',(1024,1024),im.getpixel((400,160)))
scale=min(800/chicken.width,820/chicken.height)
thumb=chicken.resize((round(chicken.width*scale),round(chicken.height*scale)),Image.Resampling.LANCZOS)
icon.paste(thumb,((1024-thumb.width)//2,(1024-thumb.height)//2))
icon.save('branding/nestlink-icon.png')
# Preserve extraction coordinates for reproducibility and future localization.
Path('design/approved-artwork-regions.json').write_text(json.dumps({'source':'approved-home-reference.png','regions':regions,'surfaces':rects},indent=2)+'\n',encoding='utf-8')
