package cs682;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class HistoryData {
    private List<Chatproto.Chat> bCastMessage;

    public HistoryData(){
        bCastMessage = Collections.synchronizedList(new ArrayList<Chatproto.Chat>());
        Chatproto.Chat chat1 = Chatproto.Chat.newBuilder().setMessage("test1").build();
        Chatproto.Chat chat2 = Chatproto.Chat.newBuilder().setMessage("test2").build();
        bCastMessage.add(chat1);
        bCastMessage.add(chat2);

    }

    void addMessage(Chatproto.Chat chat){
        this.bCastMessage.add(chat);
    }

    List<Chatproto.Chat> getbCastMessage(){
        return this.bCastMessage;
    }

    void updateHistory(List<Chatproto.Chat> bCastMessages){
        this.bCastMessage.clear();
        this.bCastMessage.addAll(bCastMessages);
    }
}
