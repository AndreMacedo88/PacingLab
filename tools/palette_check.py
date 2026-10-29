#!/usr/bin/env python3
"""Check chart colours for colour-vision deficiency and contrast. Standard library only.

Prints each colour's WCAG contrast against a surface, then the OKLab distance (x100) of every pair
under normal vision and simulated protanopia, deuteranopia and tritanopia (Machado et al. 2009,
severity 1). Project thresholds: a mark against the out-of-range orange >= 10 under protanopia and
deuteranopia; neighbours in one chart >= 15 in normal vision; marks >= 3:1 against the card.

    tools/palette_check.py '#F4F1F8' hrv=#2A78D6 out=#D9480F spo2=#B5285C
"""
import sys, math
M = {
 'protan': [[0.152286,1.052583,-0.204868],[0.114503,0.786281,0.099216],[-0.003882,-0.048116,1.051998]],
 'deutan': [[0.367322,0.860646,-0.227968],[0.280085,0.672501,0.047413],[-0.011820,0.042940,0.968881]],
 'tritan': [[1.255528,-0.076749,-0.178779],[-0.078411,0.930809,0.147602],[0.004733,0.691367,0.303900]],
}
def lin(c): c/=255; return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
def rgb(h): h=h.lstrip('#'); return [lin(int(h[i:i+2],16)) for i in (0,2,4)]
def oklab(r):
    l=0.4122214708*r[0]+0.5363325363*r[1]+0.0514459929*r[2]
    m=0.2119034982*r[0]+0.6806995451*r[1]+0.1073969566*r[2]
    s=0.0883024619*r[0]+0.2817188376*r[1]+0.6299787005*r[2]
    l,m,s=[math.copysign(abs(x)**(1/3),x) for x in (l,m,s)]
    return (0.2104542553*l+0.7936177850*m-0.0040720468*s, 1.9779984951*l-2.4285922050*m+0.4505937099*s, 0.0259040371*l+0.7827717662*m-0.8086757660*s)
def sim(r,k): return [max(0,min(1,sum(M[k][i][j]*r[j] for j in range(3)))) for i in range(3)]
def d(a,b): return 100*math.dist(oklab(a),oklab(b))
def lum(r): return 0.2126*r[0]+0.7152*r[1]+0.0722*r[2]
surface=sys.argv[1]; named=[a.split('=') for a in sys.argv[2:]]
for n,h in named:
    L1,L2=lum(rgb(h)),lum(rgb(surface)); print(f'{n:10} {h} contrast {(max(L1,L2)+0.05)/(min(L1,L2)+0.05):.2f}')
for i,(n1,h1) in enumerate(named):
    for n2,h2 in named[i+1:]:
        a,b=rgb(h1),rgb(h2)
        print(f'{n1:>10}-{n2:10} normal {d(a,b):5.1f}  ' + '  '.join(f'{k} {d(sim(a,k),sim(b,k)):5.1f}' for k in M))
