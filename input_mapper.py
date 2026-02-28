import json

class InputMapper:
    def __init__(self, socket_adapter, phone_width=720, phone_height=1280):
        self.socket_adapter = socket_adapter
        self.phone_width = phone_width
        self.phone_height = phone_height
                
    def send_tap(self, ui_x, ui_y, ui_width, ui_height):
        px = (ui_x / ui_width) * self.phone_width
        py = (ui_y / ui_height) * self.phone_height
        
        cmd = json.dumps({"type": "tap", "x": px, "y": py})
        self.socket_adapter.send_control(cmd)

    def send_swipe(self, start_x, start_y, end_x, end_y, ui_width, ui_height, duration_ms=300):
        px1 = (start_x / ui_width) * self.phone_width
        py1 = (start_y / ui_height) * self.phone_height
        px2 = (end_x / ui_width) * self.phone_width
        py2 = (end_y / ui_height) * self.phone_height
        
        cmd = json.dumps({
            "type": "swipe", 
            "x1": px1, "y1": py1, 
            "x2": px2, "y2": py2, 
            "duration": duration_ms
        })

        self.socket_adapter.send_control(cmd)
