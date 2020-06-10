package my.final_project;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    // Debugging
    private static final String TAG = "TEST+BTService";
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Intent request code
    private static final int REQUEST_CONNECT_DEVICE = 1;  // 연결이 됐는지 확인하는 변수
    private static final int REQUEST_ENABLE_BT = 2;       // 블루투스를 사용할 수 있는 장비인지 구별하는 변수

    private BluetoothAdapter btAdapter;

    private Activity mActivity;
    private Handler mHandler;


    public static final int STATE_NONE = 1; // 아무것도 하지 않을 때
    public static final int STATE_LISTEN = 2; // 연결을 위해 리스닝에 들어갈 때
    public static final int STATE_CONNECTING = 3; // 연결 과정이 이루어 질 때
    public static final int STATE_CONNECTED = 4; // 기기 사이에서의 연결이 이루어 졌을 때
    public static final int STATE_FAIL = 7; // 연결이 실패 했을 때

    int mState; // 블루투스의 상태를 나타내주는 변수
    public int mMode;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    // Constructors
    public BluetoothService(Activity ac, Handler h) {
        mActivity = ac;
        mHandler = h;

        // BluetoothAdapter 얻기
        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }
    public void set(Activity ac, Handler h) {
        mActivity = ac;
        mHandler = h;
    }
    // 디바이스가 블루투스가 가능한지 체크하는 메소드
    public boolean getDeviceState() {
        Log.i(TAG, "Check the Bluetooth support");

        if(btAdapter == null) {
            Log.d(TAG, "Bluetooth is not available");
            return false;

        } else {
            Log.d(TAG, "Bluetooth is available");
            return true;
        }
    }

    // 블루투스 Off 일 경우 활성화를 요청하는 메소드
    public void enableBluetooth() {
        Log.i(TAG, "Check the enabled Bluetooth");

        if(btAdapter.isEnabled()) {
            // 기기의 블루투스 상태가 On인 경우
            Log.d(TAG, "Bluetooth Enable Now");
            // Next Step
        } else {
            // 기기의 블루투스 상태가 Off인 경우
            Log.d(TAG, "Bluetooth Enable Request");

            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(i, REQUEST_ENABLE_BT);
        }
    }
    // 블루투스 디바이스를 찾는 메소드
    public void scanDevice() {
        Log.d(TAG, "Scan Device");
        Intent intent = new Intent(mActivity, DeviceListActivity.class);
        mActivity.startActivityForResult(intent,REQUEST_CONNECT_DEVICE);
    }
    // 블루투스의 상태를 setting
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // 핸들러를 통해 상태를 메인에 넘겨준다.
        mHandler.obtainMessage(InitialActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    // 블루투스 상태를 가져옴
    public synchronized int getState() {
        return mState;
    }
    // Thread 서비스 시작
    public synchronized void start() {
        Log.d(TAG,"start");

        if(connectThread == null) {

        }
        else {
            connectThread.cancel();
            connectThread = null;
        }
    }
    // 연결한 블루투스 기기의 주소를 connet 메소드에 넘기는 메소드
    public void getDeviceInfo(Intent data) {
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        Log.d(TAG,"Get Device Info \n address : " + address);
        connect(device);
    }
    // ConnectThread 초기화 시키고 시작, Device 의 모든 연결 제거
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG,"connect to : " + device);
        if(mState == STATE_CONNECTING) {
            if(connectThread == null) {

            }
            else {
                connectThread.cancel();
                connectThread = null;
            }
        }
        if(connectedThread == null) {

        }
        else {
            connectedThread.cancel();
            connectedThread = null;
        }
        connectThread = new ConnectThread(device); // connectThread 초기화
        connectThread.start(); // connectThread 시작
        setState(STATE_CONNECTING); // 블루투스의 상태를 STATE_CONNECTING 으로 설정
    }
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_NONE);
    }
    public synchronized void connected(BluetoothSocket socket,
                                       BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (connectThread == null) {

        } else {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread == null) {

        } else {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(STATE_CONNECTED);
    }
    public void write(byte[] out, int mode) { // Create temporary object
        ConnectedThread r; // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED)
                return;
            r = connectedThread;
        } // Perform the write unsynchronized

        r.write(out, mode);
    }
    /* connectionFailed() : 연결 실패했을때 */
    private void connectionFailed() {
        setState(STATE_FAIL);
    }

    /* connectionLost() : 연결을 잃었을 때 */
    private void connectionLost() {
        setState(STATE_LISTEN);
    }

    private class ConnectThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device)
        {
            mmDevice = device;
            BluetoothSocket tmp = null;

            //디바이스 정보를 얻어서 BluetoothSocket 생성
            try
            {
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            }
            catch(IOException e)
            {
                Log.e(TAG, "create() failed",e);
            }
            mmSocket = tmp;
        }

        public void run()
        {
            Log.i(TAG, "BEGIN connectThread");
            setName("ConnectThread");

            // 연결을 시도하기 전에는 항상 기기 검색을 중지한다.
            // 기기 검색이 계속되면 연결속도가 느려지기 때문이다.
            btAdapter.cancelDiscovery();

            // BluetoothSocket 연결 시도
            try
            {
                // BluetoothSocket 연결 시도에 대한 return 값은 succes 또는 exception이다.
                mmSocket.connect();
                Log.d(TAG, "Connect Success");
            }
            catch(IOException e)
            {
                connectionFailed(); //연결 실패 시 불러오는 메소드
                Log.d(TAG, "Connect Fail");

                //소켓을 닫는다.
                try
                {
                    mmSocket.close();
                }
                catch(IOException e2)
                {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                //연결 중 혹은 연결 대기상태인 메소드를 호출
                BluetoothService.this.start();
                return;
            }
            // ConnectThread 클래스를 reset한다.
            synchronized (BluetoothService.this) {
                connectThread = null;
            }
            // ConnectThread를 시작한다.
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // BluetoothSocket의 inputstream 과 outputstream을 얻는다.
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN connectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
            int bytes_tmp = 0;

            String temp = "";
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // InputStream으로부터 값을 받는 읽는 부분(값을 받는다)
                    Log.d(TAG,"READ");
                    bytes = mmInStream.read(buffer);
                    Log.d(TAG, "byte = " + bytes);
                    String tmp = new String(buffer,0,bytes);

                    if(tmp.charAt(bytes-1) != '/'){
                        temp += tmp;
                    }
                    else {
                        temp += tmp;

                        Log.d(TAG, "receive success" + temp);
                        mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, 1, 1, temp).sendToTarget();
                        temp = "";
                    }

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer, int mode) {
            try {
                // 값을 쓰는 부분(값을 보낸다)
                mmOutStream.write(buffer);
                mMode = mode;

                if (mode == MainActivity.MODE_REQUEST) {
                    mHandler.obtainMessage(MainActivity.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
                }

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


}
