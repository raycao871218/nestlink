#!/usr/bin/env python3
"""Package the approved yellow-chicken artwork. Requires Pillow."""
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
image=Image.open(ROOT/'branding/nestlink-icon.png').convert('RGB')
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    image.resize((size,size),Image.Resampling.LANCZOS).save(ROOT/f'app/src/main/res/mipmap-{density}/ic_launcher.png')
for target,size in [('app/src/main/res/drawable-nodpi/chicken_badge.png',256),('app/src/main/res/drawable-nodpi/chicken_launcher.png',432),('ios/App/Assets.xcassets/ChickenBadge.imageset/chicken.png',256),('ios/App/Assets.xcassets/AppIcon.appiconset/AppIcon.png',1024)]:
    image.resize((size,size),Image.Resampling.LANCZOS).save(ROOT/target)
print('Updated Android and iOS chicken icons')
