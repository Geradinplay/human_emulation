import ChatTTS
import torch
import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel
import soundfile as sf
import io
from fastapi.responses import Response

app = FastAPI()
chat = ChatTTS.Chat()
chat.load_models() # Загрузит модели в volume при первом старте

# Фиксируем голос для постоянства персонажа
spk_stat = chat.sample_random_speaker()

class SpeechRequest(BaseModel):
    text: str
    temperature: float = 0.3
    top_p: float = 0.7

@app.post("/say")
async def generate(request: SpeechRequest):
    params_infer_code = {
        'spk_stat': spk_stat,
        'temperature': request.temperature,
        'top_P': request.top_p
    }

    # Генерируем аудио (возвращает список numpy массивов)
    wavs = chat.infer([request.text], params_infer_code=params_infer_code)

    # Конвертируем в байты WAV
    buffer = io.BytesIO()
    sf.write(buffer, wavs[0].T, 24000, format='WAV')

    return Response(content=buffer.getvalue(), media_type="audio/wav")

if __name__ == "__main__":
    # Важно: 0.0.0.0 для доступа извне контейнера
    uvicorn.run(app, host="0.0.0.0", port=8080)