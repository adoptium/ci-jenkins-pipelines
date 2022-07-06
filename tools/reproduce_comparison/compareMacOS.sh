#!/bin/bash

set -e

help()
{
   echo "Run comparision for JDK MacOS release."
   echo
   echo "Syntax: $0"
   echo "Usage: $0 <jdk_folder1> <jdk_folder2> <certificate> [skip]"
   echo "arguments:"
   echo "jdk_folder1     Directory to one JDK"
   echo "jdk_folder2     Directory to another JDK"
   echo "certificate     Path to local certificate file"
   echo "skip            Skip final compare step, used for debug or from Jenkins. default to run comparision"
}

if [ ! -f "$3" ]; then
    echo "Error: Require valid local Certificate specified"
    help
    exit 1
fi
if [ ! -d "$1" ] || [ ! -d "$2" ]; then
    echo "Error: Require two valid JDK folders to compare"
    help
    exit 1
fi

CERT="$3"

for JDK_DIR in $1 $2
do
    # Expand JDK so Signatures can be removed and compared
    echo "Start Expanding: $JDK_DIR"

    if [ ! -d "${JDK_DIR}/Contents" ]; then
        echo "Error: $JDK_DIR does not contain the MacOS JDK Contents directory"
        help
        exit 1
    fi

    echo "Unpacking jmods, containing Signed dylib's"
    FILES=$(find "${JDK_DIR}" -type f -path '*.jmod')
    for f in $FILES
    do
        echo "Unpacking jmod $f"
        dir=$(dirname "$f")
        filename=$(basename "$f")
        jmod extract --dir "$dir/$filename.unzipped" "$filename"
        rm $filename
    done

    echo "Expanding the 'modules' Image"
    jimage extract --dir "${JDK_DIR}/Contents/Home/lib/modules_expanded" "${JDK_DIR}/Contents/Home/lib/modules"
    rm "${JDK_DIR}/Contents/Home/lib/modules"

    for f in "${JDK_DIR}/Contents/Home/lib/modules_expanded/java.base/jdk/internal/module/SystemModules\$0.class" \
        "${JDK_DIR}/Contents/Home/lib/modules_expanded/java.base/jdk/internal/module/SystemModules\$all.class" \
        "${JDK_DIR}/Contents/Home/lib/modules_expanded/java.base/jdk/internal/module/SystemModules\$default.class" \
        "${JDK_DIR}/Contents/Home/jmods/java.base.jmod.unzipped/classes/module-info.class" \
        "${JDK_DIR}/Contents/Home/lib/modules_expanded/java.base/module-info.class"
    do
        echo "javap converting 'exception' case from file ${f}"
        javap -p -c -l -s -constants "${f}" | grep -v bipush > "${f}.javap"
        rm "${f}"
    done

    echo "Done Expanding!"

    # Remove any extended app attr
    xattr -c "$JDK_DIR"

    # Remove any signatures
    echo "Start Removing non-deterministic signatures from $JDK_DIR"

    if [ ! -d "${JDK_DIR}/Contents" ]; then
        echo "Error: $JDK_DIR does not contain the MacOS JDK Contents directory"
        exit 1
    fi

    FILES=$(find "${JDK_DIR}" \( -type f -and -path '*.dylib' -or -path '*/bin/*' -or -path '*/lib/*' -not -path '*/modules_expanded/*' -or -path '*/jpackageapplauncher*' \))
    for f in $FILES
    do
        echo "# Removing any signatures from $f so we can compare the JDKs"
        codesign --remove-signature $f
        echo "# Signing $f with a local certificate so we can compare the JDKs"
        # Sign both with same local Certificate, this adjusts __LINKEDIT vmsize identically
        codesign -s "$CERT" --options runtime -f --timestamp "$f"
        echo " # Remove local Certificate from $f so we can compare JDKs easier."
        codesign --remove-signature $f
    done

    echo "Done Removing Signature!"
done
echo "All preparation done!"

if [ "$4" == "skip" ]; then
    echo "Skip final comparision"
    exit 0
fi

echo "Start Comparing JDKs $1 and $2"
diff -q -r "$1" "$2"
echo "Done Comparing: JDKs are identical!"