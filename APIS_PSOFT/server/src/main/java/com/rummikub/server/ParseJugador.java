package com.rummikub.server;

import java.util.List;


import java.util.ArrayList;
//CLASEE PARA FUTURO USO EN CAPA SERVICE   
//clase para gestion y edicion de la informacion de fichas jhugador


public class ParseJugador {
    private static final String SEPARADOR = ";";

    public static DataJugador parse(String entrada){
        //con un formato entrada(fichasJugadoer en el e/r) llenammos el tipo de dato antes definido
        if(entrada != null && !entrada.isEmpty()){
            //llena campos con la info necesaria usando ";"como separador
            String[] campos = entrada.split(SEPARADOR);
            DataJugador Data = new DataJugador();
            //tipo
            Data.setTipo(campos[0]);
            //id
            Data.setIdJugador(campos[1]);
            //dinero
            Data.setPuntos(Integer.parseInt(campos[2]));
            //fichas
            List<String> fichas = new ArrayList<>();
            for(int i = 3;i < campos.length;i++){
                fichas.add(campos[i]);
            }
            Data.setFichas(fichas);
            return Data;
        //REVISION DE ESTE CASO, LO MEJOR ES DEVOLVER ERROR
        //si no es valido devolvemos vacio(no se cuanto de correcto es esto, pero de nmomento sirve
        }else{
            return new DataJugador();
        }
    }

    //NECESITAMOS FUNCION DE CONVERSION DE DATOS 
    public static String  dataToText(DataJugador entrada){
        StringBuilder stringEnFormato = new StringBuilder();

        stringEnFormato.append(entrada.getTipo());
        stringEnFormato.append(SEPARADOR);
        stringEnFormato.append(entrada.getIdJugador());
        stringEnFormato.append(SEPARADOR);
        stringEnFormato.append(entrada.getPuntos());
        
        if(entrada.getFichas() != null){//si hay fichas
            for(String ficha : entrada.getFichas()){//allmacenamos uno por uno(lista tipo python)
                stringEnFormato.append(ficha);
                stringEnFormato.append(SEPARADOR);
            }
        }
        return stringEnFormato.toString();

    }
}
