"""
This script takes a youtube url, download its audio and the english subtitle.
Then it'll chop the audio into multiple small audio files, how it chops is according to the subtitle,
Ideally you want a video that breaks sentences clearly, that's way each audio file is a complete sentence.
The file name of the gerneated audio is the transcript text of that audio.

python3 download_audio_and_split https://www.youtube.com/watch?v=0H1tm1SD7dA
"""
import os, re
import pysrt
from pydub import AudioSegment
from pytube import YouTube, Caption
import xml.etree.ElementTree as ElementTree
from html import unescape

import sys

class MyCaption(Caption):
  # Can't download srt for auto-generated captions until https://github.com/pytube/pytube/issues/1085 is fixed
  def xml_caption_to_srt(self, xml_captions):
    """Convert xml caption tracks to "SubRip Subtitle (srt)".

        :param str xml_captions:
        XML formatted caption tracks.
    """
    segments = []
    root = ElementTree.fromstring(xml_captions)[1]
    i=0
    for child in list(root):
      if child.tag == 'p':
        caption = ''
        if len(list(child))==0:
          continue
        for s in list(child):
          if s.tag == 's':
            caption += ' ' + s.text
            caption = unescape(caption.replace("\n", " ").replace("  ", " "),)
            try:
              duration = float(child.attrib["d"])/1000.0
            except KeyError:
              duration = 0.0
            start = float(child.attrib["t"])/1000.0
            end = start + duration
            sequence_number = i + 1  # convert from 0-indexed to 1.
            line = "{seq}\n{start} --> {end}\n{text}\n".format(
              seq=sequence_number,
              start=self.float_to_srt_time_format(start),
              end=self.float_to_srt_time_format(end),
              text=caption,
            )
            segments.append(line)
            i += 1
    return "\n".join(segments).strip()

def download_audio_and_subtitles(youtube_url):
  yt = YouTube(youtube_url)
  yt.bypass_age_gate()

  output_path = yt.video_id
  # Download audio
  audio_output_path = ""
  audio_stream = yt.streams.filter(only_audio=True).first()
  audio_output_path = audio_stream.download(output_path=yt.video_id, filename=f'audio.webm')
  print("Downloaded audio at ", audio_output_path)

  # Download subtitles
  yt_caption_path = ""
  for track in (yt.vid_info.get("captions", {})
                .get("playerCaptionsTracklistRenderer", {})
                .get("captionTracks", [])):
    # TODO: make sure it works with uploaded caption too
    if track["vssId"]== "a.en":
      yt_caption_path = MyCaption(track).download(title="subtitle", output_path=yt.video_id, srt=True)
  print("Downloaded subtitle at ", yt_caption_path)
  return [audio_output_path, yt_caption_path]

def remove_sub_subtitles(subtitle_data):
  "The srt subtitiles will break a sentence into multiple lines, we don't need tho, we only need the full sentence"
  new_subtitle_data = []
  last_subtitle_data = subtitle_data[0]
  for i, subtitle_data in enumerate(subtitle_data[1:]):
    # if the duration changed, it means it's a new subtitle
    if not (last_subtitle_data[0] == subtitle_data[0] and last_subtitle_data[1] == subtitle_data[1]):
      new_subtitle_data.append(last_subtitle_data)
    last_subtitle_data = subtitle_data
  return new_subtitle_data

def parse_subtitle(subtitle_file):
  subtitle_data = []
  subs = pysrt.open(subtitle_file)
  for sub in subs:
    start_time = sub.start.ordinal
    end_time = sub.end.ordinal
    text = sub.text
    subtitle_data.append((start_time, end_time, text))
  return remove_sub_subtitles(subtitle_data)

def split_audio(audio_file, subtitle_file):
  output_path = "/".join(audio_file.split("/")[:-1]) + "/split_audio"
  os.makedirs(output_path, exist_ok=True)
  min_words = 3
  print(f"Splitting audio and subtitles, removing any file with less than {min_words} words, saving to {output_path}")
  subtitle_data = parse_subtitle(subtitle_file)
  audio = AudioSegment.from_file(audio_file)

  for i, (start_time, end_time, text) in enumerate(subtitle_data):
    text = text.strip()
    if len(text.split(" ")) < min_words:
      continue
    print(f"Text: {text}")
    segment = audio[start_time:end_time]
    segment.export(f"{output_path}/{text}.wav", format="wav")
  print("Done!, saved to ", output_path)

if __name__ == "__main__":
  video_url = sys.argv[1]
  [audio_file, subtitle_file] = download_audio_and_subtitles(video_url)
  split_audio(audio_file, subtitle_file)
