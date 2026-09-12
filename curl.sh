#!/usr/bin/env bash
set -e

BASE=http://127.0.0.1:5034

# 1. Upload
UP=$(curl -s -c cookies.txt -F "files=@a.pdf" -F "files=@b.pdf" $BASE/api/upload-batch)
echo "Upload: $UP"

# 2. Extract file_ids (requires jq)
IDS=$(echo "$UP" | jq -c '[.files[].file_id]')
echo "IDs: $IDS"

# 3. Convert
CV=$(curl -s -b cookies.txt -X POST $BASE/api/convert-batch \
  -H "Content-Type: application/json" \
  -d "{\"file_ids\": $IDS, \"target_format\": \"md\"}")
echo "Convert: $CV"

# 4. Download ZIP
OUT_IDS=$(echo "$CV" | jq -c '[.outputs[].output_id]')
curl -s -b cookies.txt -X POST $BASE/api/download-all \
  -H "Content-Type: application/json" \
  -d "{\"output_ids\": $OUT_IDS}" \
  -o documents.zip

echo "Saved documents.zip"
