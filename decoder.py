import av
import queue
import numpy as np

class H264Decoder:
    def __init__(self):
        self.codec = av.CodecContext.create('h264', 'r')
        self.frame_queue = queue.Queue(maxsize=30)
        self.running = False
    
    def start(self):
        self.running = True
        
    def decode_chunk(self, chunk):
        if not self.running:
            return
        try:
            packets = self.codec.parse(chunk)
            for packet in packets:
                frames = self.codec.decode(packet)
                for frame in frames:
                    img = frame.to_ndarray(format='bgr24')
                    if not self.frame_queue.full():
                        self.frame_queue.put(img)
        except Exception as e:
            pass # Ignore av.error.InvalidDataError common in raw streams until keyframe

    def get_frame(self):
        try:
            return self.frame_queue.get_nowait()
        except queue.Empty:
            return None
            
    def stop(self):
        self.running = False
