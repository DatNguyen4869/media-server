Nginx server structure
project/
├── docker-compose.yml
├── nginx/
│   └── nginx.conf
├── hls/
├── logs/

cd /Media-Server

docker-compose down
<!-- docker-compose up -d -->
docker-compose up -d --build


#instal ffmpeg in local PowerShell
winget install ffmpeg

#check install ffmpeg
ffmpeg -version


#Lệnh test 

#Tạo folder test,test_1,test_2 trong /hls 

ffmpeg -i upload/test.mp4 -c:v libx264 -c:a aac -preset veryfast -hls_time 5 -hls_playlist_type vod -hls_segment_filename ./hls/test/segment_%03d.ts ./hls/test/index.m3u8

ffmpeg -i upload/test_1.mp4 -c:v libx264 -c:a aac -preset veryfast -hls_time 5 -hls_playlist_type vod -hls_segment_filename ./hls/test_1/segment_%03d.ts ./hls/test_1/index.m3u8

ffmpeg -i upload/test_2.mkv -c:v libx264 -c:a aac -preset veryfast -hls_time 5 -hls_playlist_type vod -hls_segment_filename ./hls/test_2/segment_%03d.ts ./hls/test_2/index.m3u8

POST
http://localhost:8080/api/convert
Body (form-data):

file: choose a .mp4

#Kiểm tra sau test

http://localhost:9190/hls/test/index.m3u8
http://localhost:9190/hls/test_1/index.m3u8
http://localhost:9190/hls/test_2/index.m3u8

