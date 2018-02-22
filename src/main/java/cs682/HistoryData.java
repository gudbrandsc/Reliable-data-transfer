package cs682;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryData {
    private List<Chatproto.Chat> bCastMessage;

    public HistoryData(){
        bCastMessage = Collections.synchronizedList(new ArrayList<Chatproto.Chat>());
    }
    public void addMessage(Chatproto.Chat chat){
        this.bCastMessage.add(chat);
    }
    public List<Chatproto.Chat> getbCastMessage(){
        return this.bCastMessage;
    }
    public void updateHistory(List<Chatproto.Chat> bCastMessages){
        this.bCastMessage.clear();
        this.bCastMessage.addAll(bCastMessages);
    }
}
