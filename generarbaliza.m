% quiero realizar un codigo que genere un archivo de audio con una baliza de ultrasonidos con un uuid de 8 caracteres
% y una frecuencia de 20 kHz con modulacion BPSK 

function generarBaliza(uuid)
    % Verificar que el UUID tiene 8 caracteres
    if length(uuid) ~= 8
        error('El UUID debe tener exactamente 8 caracteres.');
    end
    
    % Parámetros de la baliza
    fs = 44100; % Frecuencia de muestreo
    f = 18000; % Frecuencia de la baliza (20 kHz)
    duration = 1; % Duración del audio en segundos
    
    % Generar el tiempo
    t = 0:1/fs:duration-1/fs;
    
    % Convertir el UUID a una secuencia de bits
    bits = reshape(dec2bin(uuid, 8).' - '0', 1, []); % 8 caracteres x 8 bits = 64 bits

    % Repetir cada bit para que ocupe la misma cantidad de muestras
    bits_per_second = length(bits) / duration;
    samples_per_bit = floor(length(t) / length(bits));
    bits_expanded = repelem(bits, samples_per_bit);
    % Ajustar el tamaño si es necesario
    if length(bits_expanded) < length(t)
        bits_expanded = [bits_expanded, zeros(1, length(t) - length(bits_expanded))];
    else
        bits_expanded = bits_expanded(1:length(t));
    end

    % Mapear bits a fases BPSK: 0 -> 0, 1 -> pi
    phase = pi * bits_expanded;

    % Generar la señal BPSK
    bpsk_signal = cos(2 * pi * f * t + phase);

    % Normalizar la señal
    bpsk_signal = bpsk_signal / max(abs(bpsk_signal));
    
    % Guardar el archivo de audio
    audiowrite(['baliza_' uuid '.wav'], bpsk_signal, fs);
    
    disp(['Archivo de audio generado: baliza_' uuid '.wav']);
end

% Ejemplo de uso
generarBaliza('12345679'); % Llamada a la función con un UUID de ejempl
