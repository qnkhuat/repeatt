let audioChunks = [];
let mediaRecorder;

const recordButton = document.getElementById('btn-do-record');
const stopButton = document.getElementById('btn-stop-record');
const playButton = document.getElementById('btn-play-record');
const statusDisplay = document.getElementById('recording-timer');
const audioRecordingId = "audio-recording"
const audioElement = document.getElementById(audioRecordingId);

function startRecording() {
  navigator.mediaDevices.getUserMedia({ audio: true })
    .then(function(stream) {
      mediaRecorder = new MediaRecorder(stream);

      mediaRecorder.ondataavailable = function(e) {
        audioChunks.push(e.data);
      }

      mediaRecorder.onstop = function() {
        const audioBlob = new Blob(audioChunks, { type: 'audio/wav' });
        const audioUrl = URL.createObjectURL(audioBlob);

        audioElement.src = audioUrl;
      }

      mediaRecorder.start();
      statusDisplay.textContent = 'Status: Recording';
      recordButton.disabled = true;
      stopButton.disabled = false;
      playButton.disabled = true;
    })
    .catch(function(err) {
      console.error('Error accessing microphone:', err);
    });
}

function stopRecording() {
  mediaRecorder.stop();
  statusDisplay.textContent = 'Status: Stopped Recording';
  recordButton.disabled = false;
  stopButton.disabled = true;
  playButton.disabled = false;
}

function playRecording() {
  const audioElement = document.getElementById(audioRecordingId);
  audioElement.play();
}
