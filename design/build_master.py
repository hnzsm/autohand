import numpy as np
from PIL import Image
from scipy import ndimage

SRC = 'vibe_images/logo-robot-c3b_1792157370844_c6b17512.png'
OUT = 'design/logo-master-1024.png'

img = Image.open(SRC).convert('RGB')
a = np.asarray(img).astype(int)
h, w = a.shape[:2]
lum = a.mean(axis=2)

# ---- 1. watermark removal: per-row linear interpolation across the text band
wm = np.zeros((h, w), bool)
band = (slice(945, 1000), slice(840, 985))
text = (lum[band] > 150)
wm[band][text] = True
wm = ndimage.binary_dilation(wm, iterations=3)
ys, xs = np.nonzero(wm)
for y in np.unique(ys):
    row = np.nonzero(wm[y])[0]
    lo, hi = row.min(), row.max()
    left = a[y, max(lo - 12, 0):lo - 2]
    right = a[y, hi + 3:hi + 13]
    if len(left) and len(right):
        l, r = np.median(left, axis=0), np.median(right, axis=0)
    elif len(left):
        l = r = np.median(left, axis=0)
    else:
        l = r = np.median(right, axis=0)
    t = (np.arange(lo, hi + 1) - lo) / max(hi - lo, 1)
    a[y, lo:hi + 1] = (l[:, None] * (1 - t) + r[:, None] * t).T
out = a.astype(np.uint8)

# ---- 2. transparent rounded-square badge (drops the black corners)
badge = ndimage.binary_fill_holes(~((out.max(axis=2) < 90)))
lab2, n2 = ndimage.label(badge)
center = lab2[h // 2, w // 2]
assert center > 0, 'badge detection failed'
badge = lab2 == center
alpha = ndimage.gaussian_filter(badge.astype(float), 1.2)
alpha = np.clip((alpha - 0.02) / 0.9, 0, 1)
rgba = np.dstack([out, (alpha * 255).astype(np.uint8)])
Image.fromarray(rgba, 'RGBA').save(OUT)
print('saved', OUT)
