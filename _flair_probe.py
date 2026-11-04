import io
p = r"D:/Moddevelop/.workflow/projects/kuso-penguin/runtime/flair-20261101/flair_verify.py"
src = open(p, encoding="utf-8").read()
start = src.index("max_torso = 0.0")
end = src.index("# Loop and full-motion preservation.")
new = '''max_torso = 0.0
max_limb = 0.0
max_tip = 0.0
for f in range(N):
    W = C[f]
    row = []
    for ci, sn in src_names.items():
        err = qang(qrot(W[ci]), qrot(frames[f][sn]))
        max_torso = max(max_torso, err)
        row.append("%s=%.4f" % (sn, err))
    limb = []
    for ci, a, b in [(4, 'RightArm', 'RightForeArm'), (5, 'RightForeArm', 'RightHand'),
                     (7, 'LeftArm', 'LeftForeArm'), (8, 'LeftForeArm', 'LeftHand'),
                     (10, 'RightUpLeg', 'RightLeg'), (11, 'RightLeg', 'RightFoot'),
                     (13, 'LeftUpLeg', 'LeftLeg'), (14, 'LeftLeg', 'LeftFoot')]:
        src = vsub(pos(frames[f][b]), pos(frames[f][a]))
        got = vsub(pos(W[ci+1]), pos(W[ci]))
        e = vang(got, [-x for x in src])
        max_limb = max(max_limb, e)
        limb.append("%s%.4f" % (a.replace('Right', 'R').replace('Left', 'L'), e))
    # The visible wing tip is a LOCAL offset, so rotate it by the full canonical
    # WORLD rotation (root..wingtip), never by the tip joint's local q[6]/q[9] alone.
    tips = []
    for side, ci, hand, middle, sign in [
            ('Right', 6, 'RightHand', 'RightHandMiddle1', 1.0),
            ('Left', 9, 'LeftHand', 'LeftHandMiddle1', -1.0)]:
        tip = [sign*0.78, 2.0, 0.17]
        Rw = qrot(W[ci])
        tipW = [sum(Rw[r][c]*tip[c] for c in range(3)) for r in range(3)]
        srcHand = vsub(pos(frames[f][middle]), pos(frames[f][hand]))
        e = vang(tipW, [-x for x in srcHand])
        max_tip = max(max_tip, e)
        tips.append("%s%.4f" % (side[0], e))
    lines.append("FRAME %2d t=%.3f torso %s limb %s tip %s pelvisY=%.4f" % (
        f, times[f], ' '.join(row), ' '.join(limb), ' '.join(tips),
        cframes[f]['translations'][1][1]))

'''
src = src[:start] + new + src[end:]
old_sum = 'lines.append("max torso world error=%.5f deg; max limb direction error=%.5f deg" % (max_torso, max_limb))'
new_sum = ('lines.append("max torso world error=%.5f deg; max limb direction error=%.5f deg; '
           'max wing-tip world error=%.5f deg" % (max_torso, max_limb, max_tip))')
if old_sum not in src:
    raise SystemExit("summary anchor missing")
src = src.replace(old_sum, new_sum)
anchor = "text = '\\n'.join(lines) + '\\n'"
check = '''if max(max_torso, max_limb, max_tip) > 0.02:
    raise SystemExit("directional retarget error exceeds 0.02 deg: torso=%.5f limb=%.5f tip=%.5f"
                     % (max_torso, max_limb, max_tip))
if loop_rot > 0.01 or loop_pos > 0.01 or unit > 1e-5:
    raise SystemExit("strict canonical validation failed: loopRot=%.6f loopPos=%.6f unit=%.2e"
                     % (loop_rot, loop_pos, unit))
'''
if anchor not in src:
    raise SystemExit("text anchor missing")
src = src.replace(anchor, check + anchor)
open(p, "w", encoding="utf-8").write(src)
print("patched", p)
