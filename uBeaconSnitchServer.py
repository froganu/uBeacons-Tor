from flask import Flask, request, render_template
from flask_cors import CORS
from datetime import datetime
import json

app = Flask(__name__)
CORS(app)  # Permite peticiones desde tu app Android

# Almacenamiento en memoria (para pruebas)
received_data = []

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

from flask import jsonify

@app.route('/api/last')
def api_last():
    if received_data:
        return jsonify(received_data[-1])
    else:
        return jsonify({})


@app.route('/')
def show_data():
    return render_template('data.html', data=received_data)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=4291, debug=True)
