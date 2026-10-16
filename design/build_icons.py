import os
import numpy as np
from PIL import Image, ImageDraw

MASTER = 'design/logo-master-1024.png'
RES = 'app/src/main/res'
DENS = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
SAFE = 0.667          # adaptive foreground: content inside the 66dp safe zone

master = Image.open(MASTER).convert('RGBA')

# white artwork layer, alpha from the red channel (green bg has r~0, art r~255)
m = np.asarray(master).astype(int)
alpha = np.clip((m[:, :, 0] - 64) / 160.0, 0, 1)
art = np.dstack([np.full_like(m[:, :, 0], 255),
                 np.full_like(m[:, :, 0], 255),
                 np.full_like(m[:, :, 0], 255),
                 (alpha * 255).astype(np.uint8)]).astype(np.uint8)
artwork = Image.fromarray(art, 'RGBA')


def circle_crop(im):
    s = im.size[0]
    mask = Image.new('L', (s * 4, s * 4), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, s * 4 - 1, s * 4 - 1], fill=255)
    out = im.copy()
    out.putalpha(Image.fromarray(
        np.minimum(np.asarray(out.split()[3]), np.asarray(mask.resize((s, s), Image.LANCZOS)))))
    return out


for folder, size in DENS.items():
    os.makedirs(f'{RES}/{folder}', exist_ok=True)
    master.resize((size, size), Image.LANCZOS).save(f'{RES}/{folder}/ic_launcher.png')
    circle_crop(master.resize((size, size), Image.LANCZOS)).save(f'{RES}/{folder}/ic_launcher_round.png')

    fg = size * 108 // 48                       # adaptive canvas at this density
    inner = int(round(fg * SAFE))
    off = (fg - inner) // 2
    canvas = Image.new('RGBA', (fg, fg), (0, 0, 0, 0))
    canvas.alpha_composite(artwork.resize((inner, inner), Image.LANCZOS), (off, off))
    canvas.save(f'{RES}/{folder}/ic_launcher_foreground.png')
    print(folder, 'launcher', size, 'adaptive', fg, 'content', inner)
