package com.nicoc.afinadordeguitarra;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;

import static java.lang.Math.log;
import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity {

    //---------------------------------------------------------------------------------------
    //-------------------------- Declaraciones Generales ------------------------------------
    //---------------------------------------------------------------------------------------

    // Flags de control de la aplicacion
    //private boolean USE_CPP_FFT = false;
    //private boolean USE_JAVA_FFT = true;
    private boolean stopped = true;

    // bool para botones
    private boolean bool_btn_miA = false;
    private boolean bool_btn_miG = false;
    private boolean bool_btn_si  = false;
    private boolean bool_btn_la  = false;
    private boolean bool_btn_re  = false;
    private boolean bool_btn_sol = false;


    // Imagenes
    private ImageView img_mia;
    private ImageView img_mig;
    private ImageView img_si;
    private ImageView img_la;
    private ImageView img_sol;
    private ImageView img_re;

    // Los valores fueron sacados de internet
    // midiendo con un analizador para celu
    // sacado de google play. Nombre "FFT Spectrum"
    // link de la pagina de notas afinadas: https://www.guitarristas.info/tutoriales/como-afinar-guitarra-todo-sobre-afinacion/2787
    // El valor del delta lo puse en 10 para probar. Lo mismo con el ruido.
    private float DELTA   = 2.0f;         //Hz
    private float RUIDO   = 50.0f;         //Hz
    private float NOTAMIA = 329.63f; //(float) 329.63;       //2308; //Hz
    private float NOTASI  = 246.94f;       //495; //Hz
    private float NOTASOL = 196.0f; // (float) 196.00;       //393; //Hz ----- armonico en 392
    private float NOTARE  = 146.83f;       //441; //Hz
    private float NOTALA  = 110.0f; //(float) 110.00;       //332; //Hz  ----- armonico en 330
    private float NOTAMIG = 82.41f;        //248; //Hz


    // Con este flag avisamos que hay data nueva a la FFT, es un semaforo mal hecho
    boolean buffer_ready = false;

    //private float freq = 0.0f;
    //private float freq_armonico = 0.0f;
    private float fundamental_freq = 0.0f;
    //private float PASOS95 = 1.0120f;
    private float PASOS = 2.3530150f;


    // Defino los buffers, potencia de 2 para mas placer y por la FFT
    private int POW_FREC_SHOW = 11;
    private int POW_FFT_BUFFER = 16;

    private int BUFFER_SIZE_SHOW_FREQ = (int) Math.pow(2,POW_FREC_SHOW);
    private int BUFFER_SIZE = (int) Math.pow(2,POW_FFT_BUFFER);
    private float[] buffFrec = new float[BUFFER_SIZE_SHOW_FREQ];

    private float[] hanning = new float[BUFFER_SIZE];

    //---------------------------------------------------------------------------------------
    //-------------------------- Libreria FFT JAVA ------------------------------------------
    //---------------------------------------------------------------------------------------
    // Creamos la clase para hacer la FFT
    // ver:  https://github.com/wendykierp/JTransforms
    // Para que esto ande debemos poner la ependencia en "build.gradle (Module: app)" :
    // dentro de "dependencies" ponemos:
    // implementation 'com.github.wendykierp:JTransforms:3.1'
    private DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);
    // Este es el buffer de entrada a la FFT, que quiere doubles...
    double[] buffer_double = new double[BUFFER_SIZE];

    //---------------------------------------------------------------------------------------
    //-------------------------- Captura de audio -------------------------------------------
    //---------------------------------------------------------------------------------------
    // Declaramos la clase para grabar audio
    AudioRecord recorder = null;
    private int SAMPLE_RATE = 44100; // en Hz
    //private int SAMPLE_RATE = getMinSupportedSampleRate(); // en Hz
    // Buffer donde sale el valor crudo del microfono
    short[] buffer = new short[BUFFER_SIZE];

    //---------------------------------------------------------------------------------------
    //-------------------------- Libreria de ploteo de señales ------------------------------
    //---------------------------------------------------------------------------------------
    // Aca abajo van las declaraciones de ploteo....
    // ver: https://github.com/PhilJay/MPAndroidChart
    // Para que esto ande debemos poner la ependencia en "build.gradle (Module: app)" :
    // dentro de "dependencies" ponemos:
    // implementation 'com.github.PhilJay:MPAndroidChart:v2.2.4'

    // Creamos las clases del grafico de FFT
    private LineChart grafico_frecuencia;
    private ArrayList<Entry> LineEntry_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    private ArrayList<String> labels_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    LineDataSet dataSet_frec;
    LineData data_frec;

    //---------------------------------------------------------------------------------------
    //-------------------------- Permisos de audio ------------------------------------------
    //---------------------------------------------------------------------------------------
    // Estas funciones de aca abajo salen de la documentación de Android, es un metodo
    // que pide permisos de microfono

    // Flag del pedido
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Pedimos permiso para grabar audio RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //------------------------------------------------------------------------------------------
        //--------------- Bloqueamos la pantalla siempre en modo retrato ---------------------------
        //------------------------------------------------------------------------------------------
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //------------------------------------------------------------------------------------------
        //----------------------- Imagenes ----------------------------------------------------------
        //------------------------------------------------------------------------------------------
        img_la  = findViewById(R.id.imageView_A);
        img_si  = findViewById(R.id.imageView_B);
        img_re  = findViewById(R.id.imageView_D);
        img_sol = findViewById(R.id.imageView_G);
        img_mia = findViewById(R.id.imageView_Eagudo);
        img_mig = findViewById(R.id.imageView_Egrave);

        //------------------------------------------------------------------------------------------
        //----------------------- Botones ----------------------------------------------------------
        //------------------------------------------------------------------------------------------
        final Button btn_Miagudo = findViewById(R.id.btn_MiAgudo);
        final Button btn_Migrave = findViewById(R.id.btn_MiGrave);
        final Button btn_Si      = findViewById(R.id.btn_Si);
        final Button btn_La      = findViewById(R.id.btn_La);
        final Button btn_Sol     = findViewById(R.id.btn_Sol);
        final Button btn_Re      = findViewById(R.id.btn_Re);

        //------------------------------------------------------------------------------------------
        //----------------------- Acciones de los botones ----------------------------------------------------------
        //------------------------------------------------------------------------------------------
        btn_Miagudo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bool_btn_miA = true; bool_btn_la = false; bool_btn_miG = false; bool_btn_re = false; bool_btn_si = false; bool_btn_sol = false;
            }
        });
        btn_La.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bool_btn_miA = false; bool_btn_la = true; bool_btn_miG = false; bool_btn_re = false; bool_btn_si = false; bool_btn_sol = false;
            }
        });
        btn_Migrave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bool_btn_miA = false; bool_btn_la = false; bool_btn_miG = true; bool_btn_re = false; bool_btn_si = false; bool_btn_sol = false;
            }
        });
        btn_Re.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bool_btn_miA = false; bool_btn_la = false; bool_btn_miG = false; bool_btn_re = true; bool_btn_si = false; bool_btn_sol = false;
            }
        });
        btn_Si.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bool_btn_miA = false; bool_btn_la = false; bool_btn_miG = false; bool_btn_re = false; bool_btn_si = true; bool_btn_sol = false;
            }
        });
        btn_Sol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bool_btn_miA = false; bool_btn_la = false; bool_btn_miG = false; bool_btn_re = false; bool_btn_si = false; bool_btn_sol = true;
            }
        });


        //------------------------------------------------------------------------------------------
        //----- Buscamos las implementaciones en el activity_main.xml de los dos graficos ----------
        //------------------------------------------------------------------------------------------
        grafico_frecuencia = findViewById(R.id.line_chart_frecuencia);

        //------------------------------------------------------------------------------------------
        //--------------------- Pedimos permiso para grabar audio ----------------------------------
        //------------------------------------------------------------------------------------------
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        // Creamos el grabador
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);

        //------------------------------------------------------------------------------------------
        //------------------------- HANNING - VENTANA ----------------------------------------------
        //------------------------------------------------------------------------------------------
        for(int i = 0; i < BUFFER_SIZE ; i++)
        {
            // Funcion sacada de MATLAB
            hanning[i] =(float)( 0.5 - (0.5 * Math.cos( (2*Math.PI*i) / (BUFFER_SIZE-1) )) );
        }

        //------------------------------------------------------------------------------------------
        //------------------ Llenamos el buffer con el mapeo de frecuencias ------------------------
        //------------------------------------------------------------------------------------------
        buffFrec[0]=0;
        for(int i=1;i<BUFFER_SIZE_SHOW_FREQ;i++)
        {
            // El valor 2,353 se saco de manera expiremental midiendo distintas
            // frecuencias y ajustando hasta tener un valor aceptable.
            buffFrec[i] = buffFrec[i-1] + (PASOS);

        }


        //------------------------------------------------------------------------------------------
        //-------------------------- INICIALIZO LOS GRAFICOS ---------------------------------------
        //------------------------------------------------------------------------------------------


        // Llenamos los buffers de señal a plotear con nada...
        for(int i=0;i<BUFFER_SIZE_SHOW_FREQ;i++)
        {
            LineEntry_frecuencia.add(new Entry(0.0f, i));//(int)buffFrec[i]));
            labels_frecuencia.add(String.valueOf(i));
        }
        // Cargamos los datos en la clase que grafica
        dataSet_frec = new LineDataSet(LineEntry_frecuencia, "Frecuencia");
        data_frec = new LineData(labels_frecuencia, dataSet_frec);

        // Seteamos los datos iniciales en los graficos
        grafico_frecuencia.setData(data_frec);

        // Configuramos los ejes de los graficos (Esto es cosmetico mas que nada)

        XAxis xl_f = grafico_frecuencia.getXAxis();
        xl_f.setTextColor(Color.WHITE);
        xl_f.setDrawGridLines(true);
        xl_f.setAvoidFirstLastClipping(true);
        xl_f.setSpaceBetweenLabels(5);
        xl_f.setEnabled(true);

        YAxis leftAxis_f = grafico_frecuencia.getAxisLeft();
        leftAxis_f.setTextColor(Color.WHITE);
        leftAxis_f.setDrawGridLines(true);

        YAxis rightAxis_f = grafico_frecuencia.getAxisRight();
        rightAxis_f.setEnabled(false);

        grafico_frecuencia.setDescription("FFT");
        //------------------------------------------------------------------------------------------
        //-------------------------- FIN INICIALIZACION DE GRAFICOS --------------------------------
        //------------------------------------------------------------------------------------------

        //------------------------------------------------------------------------------------------
        //-------------------------- EJECUTO THREADS DE PROCESO ------------------------------------
        //------------------------------------------------------------------------------------------
        // Como tiene que funcionar en paralelo, necesitamos un par de threads

        //---------------------------------- CALCULO FFT ------------------------------------------
        // Este thread espera que el grabador de audio termine y hace la FFT. Solo mira el flag,
        // si esta en flase vuelve a dormir y si es true hace FFT.
        new Thread(new Runnable() {

            @Override
            public void run() {
                while (true)
                {
                    if(!stopped)
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                calcFFT();
                            }
                        });
                    }

                    // sleep
                    try
                    {
                        Thread.sleep(10);
                    }
                    catch (InterruptedException e)
                    {

                    }
                }
            }
        }).start();
        //------------------------------------------------------------------------------------------


        //---------------------------------- EJECUTO EL THREAD DE AUDIO RECORDER -------------------
        stopped = false;
        // Este thread va a estar siempre grabando audio
        new Thread(new Runnable() {
            @Override
            public void run() {
                getDataAudio();
            }
        }).start();




    }


    //---------------------------------------------------------------------------------------
    //-------------------------- Busco el sample rate minimo soportado ----------------------
    // POR AHORA NO ESTA FUNCIONANDO
    //---------------------------------------------------------------------------------------
    private int getMinSupportedSampleRate() {
        /*
         * Valid Audio Sample rates
         *
         * @see <a
         * href="http://en.wikipedia.org/wiki/Sampling_%28signal_processing%29"
         * >Wikipedia</a>
         */
        final int validSampleRates[] = new int[] { 8000, 11025, 16000, 22050,
                32000, 37800, 44056, 44100, 47250, 48000, 50000, 50400, 88200,
                96000, 176400, 192000, 352800, 2822400, 5644800 };
        /*
         * Selecting default audio input source for recording since
         * AudioFormat.CHANNEL_CONFIGURATION_DEFAULT is deprecated and selecting
         * default encoding format.
         */
        for (int i = 0; i < validSampleRates.length; i++) {
            int result = AudioRecord.getMinBufferSize(validSampleRates[i],
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_DEFAULT);
            if (result != AudioRecord.ERROR
                    && result != AudioRecord.ERROR_BAD_VALUE && result > 0) {
                // return the mininum supported audio sample rate
                return validSampleRates[i];
            }
        }
        // If none of the sample rates are supported return -1 handle it in
        // calling method
        return -1;
    }

    //----------------------------------------------------------------------------------------------
    //------------------------------ CALC FFT - JAVA -----------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Este método hace la FFT
    private void calcFFT()
    {
        // Solo si hay nuevos datos en el buffer...
        if (buffer_ready)
        {

            // Pasamos a double como quiere la clase FFT
            for (int i = 0; i < BUFFER_SIZE; i++)
            {
                buffer_double[i] = buffer[i]*hanning[i];    // Aca estoy aplicando la ventana
            }

            // HAcemos la FFT. La salida va a estar en el mismo buffer. Solo saca la parte
            // real (izquierda) de la FFT, intercalando la salida real y la imaginaria.
            fft.realForward(buffer_double);


            // Actualizo plot
            updateFFT_values();

            // Actualizo Frecuencia
            final TextView mostrarFrec = findViewById(R.id.editText_frec);
            mostrarFrec.setText(fundamental_freq+"Hz");

            // Comparo contra la nota seteada
            NotaCmp();

            // Terminamos de procesar el buffer, reseteamos el flag
            buffer_ready = false;
        }
    }

    //----------------------------------------------------------------------------------------------
    //------------------------------ Actualizar grafico FFT ----------------------------------------
    //----------------------------------------------------------------------------------------------
    private void updateFFT_values()
    {
        // obtenemos el modulo y mostramos en el grafico de FFT
        int buffer_mod_count = 0;
        double value_max = 0;

        for (int i = 0; i < BUFFER_SIZE_SHOW_FREQ; i++)
        {
            // calculamos el modulo
            double aux_mod = sqrt(buffer_double[buffer_mod_count]*buffer_double[buffer_mod_count] + buffer_double[buffer_mod_count+1]*buffer_double[buffer_mod_count+1]);

            aux_mod = 20*log(aux_mod);

            // Valor maximo
            if(value_max < aux_mod){
                value_max = aux_mod;
                //fundamental_freq = buffFrec[i];
            }

            // Adelantamos el index del buffer con un paso grande, submuestreando la salida real
            // asi no colgamos el grafico con muchos puntos.
            buffer_mod_count += 2^(POW_FFT_BUFFER-POW_FREC_SHOW);

            // Borramos el dato
            dataSet_frec.removeFirst();
            // Agregamos un nuevo
            dataSet_frec.addEntry(new Entry((float) aux_mod, i));
        }

        // Encuentro los picos
        findPeaks();

        // Actualizamos el dataset
        data_frec.removeDataSet(0);
        data_frec.addDataSet(dataSet_frec);
        grafico_frecuencia.setData(data_frec);

        // Le avisamos que cambio y que lo actualice
        data_frec.notifyDataChanged();
        dataSet_frec.notifyDataSetChanged();
        grafico_frecuencia.invalidate();

    }

    //----------------------------------------------------------------------------------------------
    //------------------------------ ENCUENTRO PICOS Y SACO FUNDAMENTAL ----------------------------
    //----------------------------------------------------------------------------------------------
    private void findPeaks()
    {
        int buffer_mod_count = 0;
        float value_max = 0.0f;
        float freq = 0.0f;
        float buff_data[] = new float[BUFFER_SIZE_SHOW_FREQ/2];
        float buff_freq[] = new float[BUFFER_SIZE_SHOW_FREQ/2];

        // Guardo los picos
        for (int i = 0, j=0; i < BUFFER_SIZE_SHOW_FREQ; i++)
        {
            // calculamos el modulo
            double aux_mod = sqrt(buffer_double[buffer_mod_count]*buffer_double[buffer_mod_count] + buffer_double[buffer_mod_count+1]*buffer_double[buffer_mod_count+1]);

            aux_mod = 20*log(aux_mod);

            // Valor maximo
            if( aux_mod > 260) {
                if (value_max < aux_mod) {
                    value_max = (float) aux_mod;
                    freq = buffFrec[i];
                } else {
                    buff_data[j] = value_max;
                    buff_freq[j] = freq;
                    value_max = 0;
                    j++;
                }
            }


            buffer_mod_count += 2^(POW_FFT_BUFFER-POW_FREC_SHOW);
        }



        fundamental_freq = Math.abs(buff_freq[1] - buff_freq[0]);

        Log.d("Primera Frecuencia", buff_freq[0]+"");
        Log.d("Segunda Frecuencia", buff_freq[1]+"");
        Log.d("Tercera Frecuencia", buff_freq[2]+"");
        Log.d("Cuarta Frecuencia", buff_freq[3]+"");
        Log.d("Quinta Frecuencia", buff_freq[4]+"");


    }


    //----------------------------------------------------------------------------------------------
    //------------------------------ CAPTURAR AUDIO ------------------------------------------------
    //----------------------------------------------------------------------------------------------
    private void getDataAudio()
    {

        // Seteamos la prioridad
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);


        // intentamos crear el grabador de audio y grabar...
        try {



            // Empezamos a grabar
            recorder.startRecording();

            // Mientras no me digan que pare...
            while(!stopped) {

                // Leo las muestras de audio
                recorder.read(buffer,0,BUFFER_SIZE);

                // Si llego aca es que hay nueva info, seteo el flag para la FFT
                buffer_ready = true;

            }
        } catch(Throwable x) {
            //Log.w(TAG,"Error reading voice audio",x);
        } finally {
            //close();
        }
    }

    private void NotaCmp(){

        // Mi Agudo
        if(bool_btn_miA && fundamental_freq < NOTAMIA+DELTA && fundamental_freq > NOTAMIA-DELTA)
            img_mia.setBackgroundResource(R.drawable.e_verde);
        else {
            if (bool_btn_miA && fundamental_freq < RUIDO)
                img_mia.setBackgroundResource(R.drawable.e);
            else {
                if(!bool_btn_miA)
                    img_mia.setBackgroundResource(R.drawable.e);
                else
                    img_mia.setBackgroundResource(R.drawable.e_rojo);
            }
        }

        // Si
        if(bool_btn_si && fundamental_freq < NOTASI+DELTA && fundamental_freq > NOTASI-DELTA)
            img_si.setBackgroundResource(R.drawable.b_verde);
        else {
            if (bool_btn_si && fundamental_freq < RUIDO)
                img_si.setBackgroundResource(R.drawable.b);
            else {
                if(!bool_btn_si)
                    img_si.setBackgroundResource(R.drawable.b);
                else
                    img_si.setBackgroundResource(R.drawable.b_rojo);
            }
        }

        // Sol
        if(bool_btn_sol && fundamental_freq < NOTASOL+DELTA && fundamental_freq > NOTASOL-DELTA)
            img_sol.setBackgroundResource(R.drawable.g_verde);
        else {
            if (bool_btn_sol && fundamental_freq < RUIDO)
                img_sol.setBackgroundResource(R.drawable.g);
            else {
                if(!bool_btn_sol)
                    img_sol.setBackgroundResource(R.drawable.g);
                else
                    img_sol.setBackgroundResource(R.drawable.g_rojo);
            }
        }

        // Re
        if(bool_btn_re && fundamental_freq < NOTARE+DELTA && fundamental_freq > NOTARE-DELTA)
            img_re.setBackgroundResource(R.drawable.d_verde);
        else {
            if (bool_btn_re && fundamental_freq < RUIDO)
                img_re.setBackgroundResource(R.drawable.d);
            else {
                if(!bool_btn_re)
                    img_re.setBackgroundResource(R.drawable.d);
                else
                    img_re.setBackgroundResource(R.drawable.d_rojo);
            }
        }

        // La
        if(bool_btn_la && fundamental_freq < NOTALA+DELTA && fundamental_freq > NOTALA-DELTA)
            img_la.setBackgroundResource(R.drawable.a_verde);
        else {
            if (bool_btn_la && fundamental_freq < RUIDO)
                img_la.setBackgroundResource(R.drawable.a);
            else {
                if(!bool_btn_la)
                    img_la.setBackgroundResource(R.drawable.a);
                else
                    img_la.setBackgroundResource(R.drawable.a_rojo);
            }
        }

        // Mi Grave
        if(bool_btn_miG && fundamental_freq < NOTAMIG+DELTA && fundamental_freq > NOTAMIG-DELTA)
            img_mig.setBackgroundResource(R.drawable.e_verde);
        else {
            if (bool_btn_miG && fundamental_freq < RUIDO)
                img_mig.setBackgroundResource(R.drawable.e);
            else {
                if(!bool_btn_miG)
                    img_mig.setBackgroundResource(R.drawable.e);

                else
                    img_mig.setBackgroundResource(R.drawable.e_rojo);
            }
        }


    }



}