
file="api-ms-winsdfsdfsf.dll"
if [[ "$file" =~ api-ms-win.* ]] || [[ "$file" =~ msvcp.* ]] || [[ "$file" =~ ucrtbase.* ]] || [[ "$file" =~ vcruntime.* ]]; then
                                                            echo "Skipping Microsoft file $file"
fi

