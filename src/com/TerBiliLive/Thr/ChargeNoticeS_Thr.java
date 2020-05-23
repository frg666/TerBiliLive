package com.TerBiliLive.Thr;

import com.TerBiliLive.Info.ConfInfo;
import com.TerBiliLive.TerBiliLive.GetInfo;
import com.TerBiliLive.Utils.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

import static com.TerBiliLive.Ui.TerBiliLive_Control_Ui.Control_UiT_RoomId;
import static com.TerBiliLive.Ui.TerBiliLive_Control_Ui.Control_UiT_State;
import static com.TerBiliLive.Utils.TimeUtil.getFormat;
import static com.TerBiliLive.Utils.TimeUtil.getFormatDay;
import static com.TerBiliLive.Utils.TimeUtil.getFormatHour;

/**
 * CODE IS POETRY
 * @Nmae ：连接服务获取信息线程
 * @Author ：xnter@outlook.com.
 * @Date ：Created in 11:51 2018/11/4
 */

public class ChargeNoticeS_Thr {

    private Socket socket;
    private boolean keepRunning = true;
    private boolean isReConn = true;
    private String roomID;
    private GetInfo client;

    public void start(String roomID, boolean isReConnect){
        this.roomID = roomID;
        isReConn = isReConnect;
        client = new GetInfo();
        socket = client.connect(this.roomID);
        if (socket != null) {
            handle_data_loop hdp=  new handle_data_loop();
            try {
                hdp.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            hdp.start();
        }
    }

    public void stop(){
        keepRunning = false;
        client.disconnect(socket);
        String uid = "";
        String name =  "";
        ConfInfo.liveConf=null;
        // 未登录将不执行
        String putDM = "系统 ："+getFormat()+" - "+"断开连接" +" 真实直播间ID："+roomID ;
        if(null!=ConfInfo.liveUserInfo||!ConfInfo.confData.getCookie().equals("")){
            uid =  ConfInfo.liveUserInfo.getUid();
            name =  ConfInfo.liveUserInfo.getUname();
            putDM =  "系统 ："+getFormat()+" - "+"断开连接" +" 真实直播间ID："+roomID +"  UID："+uid+"  昵称："+name;
        }else{
            putDM =  "系统 ："+getFormat()+" - "+"断开连接" +" 真实直播间ID："+roomID +"  - 用户未登录(游客模式)";
        }
        ConfInfo.putShowUtil.PutDMUtil(putDM, Color.RED);
       // new PutDMUtil(putDM);
        Control_UiT_State.setText("已断开连接" );
        DmLogUtil.putDmLog(getFormatDay(), getFormatHour(),putDM,Control_UiT_RoomId.getText());
        InOutPutUtil.outPut("断开连接" +" 真实直播间ID："+roomID );
    }

    private class handle_data_loop extends Thread {
        DataInputStream input = null;


        public void run(){
            if (socket != null){
                int bufferSize = 10 * 1024;
                try {
//                    LiveInfo xx=new LiveInfo(Control_UiT_RoomId.getText());
//                    DMJ_UiT_Text.append("房间信息："+xx.toString());
                    bufferSize = socket.getReceiveBufferSize();
                    String uid = "";
                    String name =  "";
                    // 未登录将不执行
                    if(null!=ConfInfo.liveUserInfo||!ConfInfo.confData.getCookie().equals("")){
                        uid =  ConfInfo.liveUserInfo.getUid();
                        name =  ConfInfo.liveUserInfo.getUname();
                        ConfInfo.putShowUtil.PutDMUtil("系统 ："+getFormat()+" ! "+"连接成功 " +"真实直播间ID："+roomID +"  UID："+uid+"  昵称："+name,Color.BLUE);

                    } else{
                        ConfInfo.putShowUtil.PutDMUtil("系统 ："+getFormat()+" ! "+"连接成功 " +"真实直播间ID："+roomID +"  - 用户未登录(游客模式)",Color.BLUE);
                    }
                    DmLogUtil.putDmLog(getFormatDay(), getFormatHour(),"连接成功" +"真实直播间ID："+roomID ,Control_UiT_RoomId.getText());
                    InOutPutUtil.outPut("连接成功" +"真实直播间ID："+roomID );
                    ConfInfo.isReConnSum=0;
                }catch (Exception ex){
                    ex.printStackTrace();
                }
                byte[] ret = new byte[bufferSize];
                while (keepRunning){
                    try {
                        input = new DataInputStream(socket.getInputStream());
                        int retLength = input.read(ret);
                        if (retLength > 0 && keepRunning) {
                            byte[] recvData = new byte[retLength];
                            System.arraycopy(ret, 0, recvData, 0, retLength);
                            analyzeData(recvData);
                        }
                    }catch (Exception e){
                        if (isReConn && keepRunning ) {
                            ConfInfo.putShowUtil.PutDMUtil("系统 ："+getFormat()+" ! "+ConfInfo.isReConnSum+"-自动重连" +" 真实直播间ID："+roomID,Color.BLUE);

                            DmLogUtil.putDmLog(getFormatDay(), getFormatHour(),ConfInfo.isReConnSum+"-自动重连" +" 真实直播间ID："+roomID   ,Control_UiT_RoomId.getText());
                            InOutPutUtil.outPut("自动重连" +" 真实直播间ID："+roomID );

                            if(ConfInfo.isReConnSum>=10){
                                keepRunning = false;
                            }
                            (new ChargeNoticeS_Thr()).start(roomID, true);

                        }
                        keepRunning = false;
                        e.printStackTrace();
                    }
                }
//                DMJ_UiT_Text.setCaretPosition(DMJ_UiT_Text.getText().length());
            }
        }

        private void analyzeData(byte[] data){

            int dataLength = data.length;
            if (dataLength < 16){
                InOutPutUtil.outPut("错误的数据");
            }else {
                DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(data));
                try {
                    int msgLength = inputStream.readInt();
                    if (msgLength < 16){
                        InOutPutUtil.outPut("可能需要扩大缓冲区大小");
                    }else if(msgLength > 16 && msgLength == dataLength){
                        // 其实是两个char
                        inputStream.readInt();
                        int action = inputStream.readInt() - 1;
                        // 直播间在线用户数目
                        if (action == 2){
                            inputStream.readInt();
                            int userCount = inputStream.readInt();
//                            InOutPutUtil.outPut("在线人数：" + userCount);
                            Control_UiT_State.setText("人气：" + userCount);
//                            ConfInfo.putDMUtil.PutDMUtil("在线人数：" + userCount);
                            InOutPutUtil.outPut("人气：" + userCount);
                        }else if (action == 4){
                            inputStream.readInt();
                            int msgBodyLength = dataLength - 16;
                            byte[] msgBody = new byte[msgBodyLength];
                            byte[] msgBodys;
                            byte[] msgBodyfg;

                            String[] strArr = null;
                            if (inputStream.read(msgBody) == msgBodyLength){
                                if(data[7] == 2){
                                    InOutPutUtil.outPut("压缩----------------");
                                    msgBodys = ZLibUtil.decompress(msgBody);
                                    msgBodyfg = Arrays.copyOfRange(msgBodys, 0,16);
                                    msgBodys = Arrays.copyOfRange(msgBodys, 16,msgBodys.length);
                                    String jsonStr = new String(msgBodys, "utf-8");
                                    strArr = jsonStr.split(new String(msgBodyfg, "utf-8"));
                                }else{
                                    msgBodys = msgBody;
                                    String jsonStr = new String(msgBodys, "utf-8");
                                    strArr = new String[]{jsonStr};
                                }
                                for (String s : strArr) {
                                    InOutPutUtil.outPut("放入------------");
                                    InOutPutUtil.outPut(s);
                                    ConfInfo.ParsingBarrageList.add(s);
//
                                }
//                                if(jsonStr.substring(0, 1).toCharArray()[0] == '['){
//                                    JSONArray array = JSON.parseArray(jsonStr);
//                                    InOutPutUtil.outPut(jsonStr);
//                                    array.forEach(d->{
//                                        InOutPutUtil.outPut("循环-------------");
//                                        InOutPutUtil.outPut(d.toString());
//                                        // 将弹幕信息放入 list
////                                        ConfInfo.ParsingBarrageList.add(d.toString());
//                                    });
//                                }else{
//                                    InOutPutUtil.outPut(jsonStr);
////                                    ConfInfo.ParsingBarrageList.add(jsonStr);
//                                }
//
                                // 开启弹幕解析线程
                                synchronized (ConfInfo.PBT) {
                                    ConfInfo.PBT.notify();
                                }

                            }
                        }
                    }else if (msgLength > 16 && msgLength < dataLength){
                        byte[] singleData = new byte[msgLength];
                        System.arraycopy(data, 0, singleData, 0, msgLength);
                        analyzeData(singleData);
                        int remainLen = dataLength - msgLength;
                        byte[] remainDate = new byte[remainLen];
                        System.arraycopy(data, msgLength, remainDate, 0, remainLen);
                        analyzeData(remainDate);
                    }
                }catch (Exception ex){

                    ex.printStackTrace();
                }
            }
        }

    }

}
