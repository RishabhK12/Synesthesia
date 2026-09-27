// Receive direction events from the Pico -> ESP32-WROOM-32 BLE bridge.
export const SERVICE_UUID = '0066cf31-d07d-44cf-9a90-af6fb370f5e7';
export const EVENT_UUID = 'e56dd748-976b-4fd1-b7cf-7e9f480f1c7a';
const STATUSES = ['ok', 'ambiguous', 'clipped', 'rear_possible'];

export function decodeDirection(value) {
  if (!(value instanceof DataView) || value.byteLength !== 6 || value.getUint8(0) !== 1) {
    throw new Error('Unsupported sensor packet');
  }
  const flags = value.getUint8(1);
  const status = STATUSES[flags & 0x7f];
  const rearTriggered = !!(flags & 0x80);
  const rawAngle = value.getUint16(2, true);
  const rawSeparation = value.getUint8(4);
  if (!status || rawSeparation > 100 || (status === 'rear_possible' && !rearTriggered)) {
    throw new Error('Invalid sensor packet');
  }
  if (status === 'ok' ? rawAngle >= 3600 : rawAngle !== 0xffff) {
    throw new Error('Invalid sensor angle');
  }
  return {
    status,
    angle_deg: status === 'ok' ? rawAngle / 10 : null,
    separation: rawSeparation / 100,
    rear_triggered: rearTriggered,
    sequence: value.getUint8(5),
  };
}

export class DirectionSensor {
  constructor({ onEvent, onState }) {
    this.onEvent = onEvent;
    this.onState = onState;
    this.device = null;
    this.characteristic = null;
    this.connecting = false;
    this.onNotification = (notification) => {
      try {
        this.onEvent(decodeDirection(notification.target.value));
      } catch (error) {
        console.warn('Ignored malformed ESP32 event', error);
      }
    };
    this.onDisconnect = () => {
      this.characteristic = null;
      this.onState('disconnected', 'Sensor disconnected. Tap Connect sensor again.');
    };
  }

  async connect() {
    if (this.connecting || this.device?.gatt?.connected) return;
    if (!window.isSecureContext || !navigator.bluetooth) {
      this.onState('unsupported', 'Bluetooth needs HTTPS and Chrome on Android.');
      return;
    }
    this.connecting = true;
    this.onState('connecting', 'Choose SynDir in the Bluetooth picker.');
    try {
      // requestDevice must stay in the user's button-tap call chain.
      const device = await navigator.bluetooth.requestDevice({
        filters: [{ name: 'SynDir' }], optionalServices: [SERVICE_UUID],
      });
      this.device?.removeEventListener('gattserverdisconnected', this.onDisconnect);
      this.device = device;
      device.addEventListener('gattserverdisconnected', this.onDisconnect);
      const server = await device.gatt.connect();
      const service = await server.getPrimaryService(SERVICE_UUID);
      const characteristic = await service.getCharacteristic(EVENT_UUID);
      characteristic.addEventListener('characteristicvaluechanged', this.onNotification);
      await characteristic.startNotifications();
      this.characteristic = characteristic;
      this.onState('connected', 'SynDir connected. Listening for direction events.');
    } catch (error) {
      this.onState('disconnected', error.name === 'NotFoundError'
        ? 'No sensor selected. Tap Connect sensor to try again.'
        : `Sensor connection failed: ${error.message}`);
    } finally {
      this.connecting = false;
    }
  }

  disconnect() {
    this.characteristic?.removeEventListener('characteristicvaluechanged', this.onNotification);
    this.characteristic = null;
    this.device?.gatt?.disconnect();
  }
}
