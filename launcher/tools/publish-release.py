#!/usr/bin/env python3
"""Publish an already-tested APK and advance the HTTPS update manifest last."""
import argparse, hashlib, json, os, re, shlex, shutil, subprocess, tempfile
from pathlib import Path

def run(args, cwd=None):
    result=subprocess.run([str(a) for a in args],cwd=cwd,check=True,capture_output=True,text=True)
    return result.stdout.strip()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk",required=True,type=Path)
    parser.add_argument("--notes",required=True,type=Path)
    parser.add_argument("--repo",default="Thalveron/Aurora-Launcher")
    parser.add_argument("--gh",default=shutil.which("gh"))
    parser.add_argument("--aapt",type=Path)
    parser.add_argument("--apksigner",type=Path)
    parser.add_argument("--stable",action="store_true")
    args=parser.parse_args()
    root=Path(run(["git","rev-parse","--show-toplevel"]))
    if not args.gh:parser.error("Install and sign into the official GitHub CLI first.")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+",args.repo):parser.error("Invalid repository")
    if run(["git","status","--porcelain"],root):parser.error("Commit the intended public source changes first; the working tree must be clean.")
    sdk=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    tools=sorted((Path(sdk)/"build-tools").glob("*"),key=lambda p:[int(v) if v.isdigit() else 0 for v in p.name.split(".")])[-1] if sdk else None
    aapt=args.aapt or (tools/"aapt" if tools else None);signer=args.apksigner or (tools/"apksigner" if tools else None)
    if not aapt or not signer:parser.error("Set ANDROID_HOME, or provide --aapt and --apksigner.")
    apk=args.apk.resolve();notes=args.notes.resolve()
    run([signer,"verify",apk])
    badging=run([aapt,"dump","badging",apk])
    meta=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    if not meta or meta[1]!="com.gathin.tvhome" or not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?",meta[3]):parser.error("Unexpected package or version.")
    code=int(meta[2]);version=meta[3];tag="v"+version
    manifest=root/"updates/latest.json"
    existing=json.loads(manifest.read_text()) if manifest.exists() else {}
    if code<=existing.get("latest",{}).get("versionCode",0):parser.error("Increase versionCode and versionName before publishing.")
    if subprocess.run([args.gh,"release","view",tag,"--repo",args.repo],capture_output=True).returncode==0:parser.error("That release exists; publish a new version instead of replacing it.")
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    name="Aurora-Launcher-v"+version+".apk"
    with tempfile.TemporaryDirectory(prefix="aurora-release-") as temp:
        artifact=Path(temp)/name;shutil.copyfile(apk,artifact)
        checksum=Path(temp)/"SHA256SUMS.txt";checksum.write_text(digest+"  "+name+"\n")
        command=[args.gh,"release","create",tag,str(artifact),str(checksum),"--repo",args.repo,"--target",run(["git","rev-parse","HEAD"],root),"--title","Aurora Launcher "+tag,"--notes-file",str(notes)]
        if not args.stable:command.append("--prerelease")
        run(command,root)
    latest={"version":version,"versionCode":code,"minSdk":26,"url":"https://github.com/"+args.repo+"/releases/download/"+tag+"/"+name,"sha256":digest,"bytes":apk.stat().st_size,"notes":notes.read_text()[:12000]}
    history=([existing["latest"]] if existing.get("latest") else [])+existing.get("history",[])
    document={"schema":1,"latest":latest,"history":history}
    manifest.parent.mkdir(exist_ok=True);manifest.write_text(json.dumps(document,ensure_ascii=False,indent=2)+"\n")
    run(["git","add","updates/latest.json"],root)
    run(["git","commit","-m","Publish update metadata for "+tag],root)
    run(["git","-c","credential.helper=","-c","credential.helper=!"+shlex.quote(str(args.gh))+" auth git-credential","push","origin","HEAD:main"],root)
    print("Published https://github.com/"+args.repo+"/releases/tag/"+tag)
    print("The update manifest was advanced only after the APK upload completed.")
if __name__=="__main__":main()
