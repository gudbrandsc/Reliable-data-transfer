package cs682;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * @author gudbrand schistad
 * Class that is used to store history data, and updated history data when requested from an other user
 */
class HistoryData {
    private List<Chatproto.Chat> bCastMessage;

    /**
     * Constructor
     */
    HistoryData(){
        bCastMessage = Collections.synchronizedList(new ArrayList<Chatproto.Chat>());
        //Just for making fast testing without having to add broadcast messages from program each time
        Chatproto.Chat chat1 = Chatproto.Chat.newBuilder().setMessage("test1").build();
        Chatproto.Chat chat2 = Chatproto.Chat.newBuilder().setMessage("test2").build();
        bCastMessage.add(chat1);
        bCastMessage.add(chat2);

    }

    /**
     * Adds a broadcast to the history
     */
    void addMessage(Chatproto.Chat chat){
        this.bCastMessage.add(chat);
    }

    /**
     * Get the broadcast history list
     * @return
     */
    List<Chatproto.Chat> getHistoryList(){
        return this.bCastMessage;
    }

    /**
     * Replace history with downloaded history
     */
    void updateHistory(List<Chatproto.Chat> bCastMessages){
        this.bCastMessage.clear();
        this.bCastMessage.addAll(bCastMessages);
    }
}
