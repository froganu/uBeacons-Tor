from flask import Flask, request, render_template, jsonify
from flask_cors import CORS
from datetime import datetime
import time

app = Flask(__name__)
CORS(app)

received_data = []
last_index = -1

@app.route('/api/beacon-data', methods=['POST'])
def handle_data():
    try:
        data = request.get_json()
        data['received_at'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        received_data.append(data)
        print("Datos recibidos:", data)
        return {'status': 'success'}, 200
    except Exception as e:
        print("Error:", e)
        return {'status': 'error'}, 500

@app.route('/api/last')
def api_last():
    global last_index
    timeout = 30  # segundos máximo de espera
    start_time = time.time()
    while time.time() - start_time < timeout:
        if len(received_data) - 1 > last_index:
            last_index = len(received_data) - 1
            return jsonify(received_data[-1])
        time.sleep(1)
    return jsonify({})  # Si no hay datos nuevos en 30s, responde vacío

@app.route('/')
def show_data():
    return render_template('data.html', data=received_data)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8081, debug=True)
