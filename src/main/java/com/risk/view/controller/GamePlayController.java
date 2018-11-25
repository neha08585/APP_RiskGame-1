package com.risk.view.controller;

import com.risk.model.*;
import com.risk.services.MapIO;
import com.risk.services.StartUpPhase;
import com.risk.services.gameplay.RoundRobin;
import com.risk.services.saveload.SaveData;
import com.risk.view.CardView;
import com.risk.view.Util.WindowUtil;
import com.risk.services.saveload.ResourceManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

/**
 * This class provides all the methods to control various activities during the game play,
 * implements Observer.
 *
 * @author Karandeep Singh
 * @author Ruthvik Shandilya
 */
public class GamePlayController extends Observable implements Initializable, Observer {

    private static HashSet<Country> allAdjacentCountries = new HashSet<>();

    private static HashMap<Country, Boolean> visited = new HashMap<>();

    private HashMap<String,String> playerNamesAndTypes;


    /**
     * RoundRobin Object
     */
    private RoundRobin roundRobin;

    /**
     * Object to load contents of the Map file
     */
    private MapIO map;

    /**
     * Object to provide Setup Phase
     */
    private StartUpPhase startUpPhase;

    /**
     * Card Object
     */
    private Card card;

    /**
     * Object to create barChart
     */
    @FXML
    private BarChart dominationBarChart;

    /**
     * WorldDomination Object
     */
    private PlayerWorldDomination worldDomination;

    /**
     * attack Button
     */
    @FXML
    private Button attack;

    /**
     * fortify Button
     */
    @FXML
    private Button fortify;

    /**
     * endTurn Button
     */
    @FXML
    private Button endTurn;

    /**
     * reinforcement Button
     */
    @FXML
    private Button reinforcement;

    /**
     * cards Button
     */
    @FXML
    private Button cards;

    /**
     * VerticalBox for Display
     */
    @FXML
    private VBox displayBox;

    /**
     * Selected Countries ListView provides scrollable list of items
     */
    @FXML
    private ListView<Country> selectedCountryList;

    /**
     * Adjacent Countries ListView provides scrollable list of items
     */
    @FXML
    private ListView<Country> adjacentCountryList;

    /**
     * Label of selected player
     */
    @FXML
    private Label playerChosen;

    /**
     * PhaseView Label
     */
    @FXML
    private Label phaseView;

    /**
     * TextArea to which current game information will be displayed
     */
    @FXML
    private TextArea terminalWindow;

    /**
     * placeArmy Button
     */
    @FXML
    private Button placeArmy;

    private String phaseViewString;

    /**
     * selected number of players who are supposed to play the Game
     */
    private int numberOfPlayersSelected;

    private boolean isGameSaved = false;

    /**
     * List of Players
     */
    private ArrayList<Player> gamePlayerList;

    /**
     * The @playerIterator.
     */
    private Iterator<Player> playerIterator;

    /**
     * The Current Player whose playing
     */
    private Player playerPlaying;

    /**
     * Stack to store and retrieve the cards
     */
    private Stack<Card> cardStack;

    /**
     * Number of card sets exchanged
     */
    private int numberOfCardSetExchanged;

    public GamePlayController(){

    }

    public TextArea getTerminalWindow() {
        return terminalWindow;
    }


    public GamePlayController(MapIO map, HashMap<String, String> hm) {
        this.map = map;
        this.startUpPhase = new StartUpPhase();
        this.card = new Card();
        this.playerNamesAndTypes = hm;
        card.addObserver(this);
        worldDomination = new PlayerWorldDomination();
        worldDomination.addObserver(this);
        this.setNumberOfCardSetExchanged(0);
        new WindowUtil(this);
    }

    /**
     * This method helps in player creation.
     */
    public void playerCreation() {

        setNumberOfPlayersSelected(this.playerNamesAndTypes.keySet().size());
        gamePlayerList.clear();
        setChanged();
        notifyObservers("Set up phase started\n");
        gamePlayerList = new Player().generatePlayer( this.playerNamesAndTypes, terminalWindow);
        setChanged();
        notifyObservers("All players generated\n");

        playerIterator = gamePlayerList.iterator();

        new Player().assignArmiesToPlayers(gamePlayerList, terminalWindow);
        allocateCountryToPlayerInGamePlay();
        setChanged();
        notifyObservers("All countries assigned\n");

        loadMapData();
        loadCurrentPlayer();
        generateBarGraph();
        WindowUtil.enableButtonControl(cards);
    }

    /**
     * This is the initial method which loads the Game Cards,Map Contents
     * and helps in assigning the country and adjacent countries to the
     * player.
     *
     * @param location  Location
     * @param resources Resources
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        gamePlayerList = new ArrayList<>();
        playerCreation();
        loadGameCard();
        loadMapData();
        phaseView.setText("Phase: Start Up");
        WindowUtil.disableButtonControl(reinforcement, fortify, attack, cards);

        selectedCountryList.setCellFactory(param -> new ListCell<Country>() {
            @Override
            protected void updateItem(Country currentCountry, boolean empty) {
                super.updateItem(currentCountry, empty);

                if (empty || currentCountry == null || currentCountry.getName() == null) {
                    setText(null);
                } else {
                    setText(currentCountry.getName() + ":-" + currentCountry.getNoOfArmies() + "-" + currentCountry.getPlayer().getName());
                }
            }
        });
        selectedCountryList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                Country country = selectedCountryList.getSelectionModel().getSelectedItem();
                if(phaseView.getText().equals("Phase: Fortification"))
                    moveToAdjacentCountryFortification(country);
                else
                    moveToAdjacentCountry(country);
            }
        });

        adjacentCountryList.setCellFactory(param -> new ListCell<Country>() {
            @Override
            protected void updateItem(Country currentCountry, boolean empty) {
                super.updateItem(currentCountry, empty);

                if (empty || currentCountry == null || currentCountry.getName() == null) {
                    setText(null);
                } else {
                    setText(currentCountry.getName() + "-" + currentCountry.getNoOfArmies() + "-" + currentCountry.getPlayer().getName());
                }
            }
        });
    }

    /**
     * Method to get the adjacent country.
     *
     * @param country Country Object
     */
    private void moveToAdjacentCountryFortification(Country country) {
        this.adjacentCountryList.getItems().clear();
        GamePlayController.allAdjacentCountries.clear();
        for (Country country1 : this.map.getMapGraph().getCountrySet().values()) {
            GamePlayController.visited.put(country1, false);
        }
        this.findAdjacentCountriesFortification(country);
        this.findAdjacentCountriesOtherPlayers(country);
        this.adjacentCountryList.getItems().addAll(GamePlayController.allAdjacentCountries);
    }

    private void findAdjacentCountriesOtherPlayers(Country country){
        for(Country country1 : country.getAdjacentCountries()){
            if(!playerPlaying.getPlayerCountries().contains(country1)){
                GamePlayController.allAdjacentCountries.add(country1);
            }
        }
    }

    /**
     * Method to get the adjacent country.
     *
     * @param country Country Object
     */
    private void moveToAdjacentCountry(Country country) {
        this.adjacentCountryList.getItems().clear();
        if (country != null) {
            for (Country adjTerr : country.getAdjacentCountries()) {
                this.adjacentCountryList.getItems().add(adjTerr);
            }
        }
    }

    private void findAdjacentCountriesFortification(Country country) {
        if (country != null) {
            GamePlayController.visited.put(country, true);
            for (Country adjCountry : country.getAdjacentCountries()) {
                if (!GamePlayController.visited.get(adjCountry) && !GamePlayController.allAdjacentCountries.contains(adjCountry)
                    && playerPlaying.getPlayerCountries().contains(adjCountry)) {
                    GamePlayController.allAdjacentCountries.add(adjCountry);
                    findAdjacentCountriesFortification(adjCountry);
                }
            }
        }
    }


    /**
     * Method which helps in allocating cards to Countries.
     */
    private void loadGameCard() {
        cardStack = startUpPhase.assignCardToCountry(map, terminalWindow);
        setChanged();
        notifyObservers("Cards loaded\n");
    }

    /**
     * This is the Complete Attack button event. This button helps the player
     * to end attack phase and move to next phase.
     *
     * @param event End Attack Event
     */
    @FXML
    private void completeAttack(ActionEvent event) {
        adjacentCountryList.setOnMouseClicked(e -> System.out.print(""));
        if (playerPlaying.getCountryWon() > 0) {
            allocateCardToPlayer();
        }
        setChanged();
        notifyObservers("Attack phase ended\n");
        isValidFortificationPhase();
    }

    /**
     * Method to allocate cards to player
     */
    private void allocateCardToPlayer() {
        Card cardToBeAdded = cardStack.pop();
        playerPlaying.getCardList().add(cardToBeAdded);
        playerPlaying.setCountryWon(0);
        setChanged();
        notifyObservers(cardToBeAdded.getCardType() + " card is assigned to " +
                playerPlaying.getName() + " and won country " + cardToBeAdded.getCountry().getName() + "\n");
    }

    /**
     * Method to assign the attacking and defending country
     */
    private void attack() {
        Country attackingTerritory = selectedCountryList.getSelectionModel().getSelectedItem();
        Country defendingTerritory = adjacentCountryList.getSelectionModel().getSelectedItem();

        playerPlaying.attackPhase(attackingTerritory, defendingTerritory, terminalWindow);
    }

    /**
     * This is the Fortify Button Event
     *
     * @param event fortify button
     */
    @FXML
    private void fortify(ActionEvent event) {
        Country selectedCountry = this.selectedCountryList.getSelectionModel().getSelectedItem();
        Country adjacentCountry = this.adjacentCountryList.getSelectionModel().getSelectedItem();

        playerPlaying.fortificationPhase(selectedCountry, adjacentCountry, terminalWindow);
        selectedCountryList.refresh();
        adjacentCountryList.refresh();
        loadMapData();
    }

    /**
     * This is the endTurn Action event.
     *
     * @param event endTurn button
     */
    @FXML
    private void endTurn(ActionEvent event) {
        adjacentCountryList.setOnMouseClicked(e -> System.out.print(""));
        setChanged();
        notifyObservers("Player " + playerPlaying.getName() + " ended his turn.\n");
        if (playerPlaying.getCountryWon() > 0) {
            allocateCardToPlayer();
        }
        initializeReinforcement();
        CardView.openCardWindow(playerPlaying, card);
    }

    /**
     * This is the placeArmy Action event.
     *
     * @param event placeArmy button
     */
    @FXML
    private void placeArmy(ActionEvent event) {
        playerPlaying.placeArmyOnCountry(playerPlaying, selectedCountryList, gamePlayerList, terminalWindow);
    }

    /**
     * This is the reinforcement Action event.
     *
     * @param event reinforcement button
     */
    @FXML
    private void reinforcement(ActionEvent event) {
        Country country = selectedCountryList.getSelectionModel().getSelectedItem();
        if (playerPlaying.getCardList().size() >= 5) {
            WindowUtil.popUpWindow("Exchange Card", "Card exchange popup", "You have at least 5 cards please exchange for armies");
            return;
        }
        playerPlaying.reinforcementPhase(country, terminalWindow);
        selectedCountryList.refresh();
        loadMapData();
        playerChosen.setText(playerPlaying.getName() + ":- " + playerPlaying.getArmyCount() + " armies left.");
    }

    /**
     * Method to load the Map Data in the new Pane.
     */
    private void loadMapData() {
        displayBox.getChildren().clear();
        for (Continent continent : map.getMapGraph().getContinents().values()) {
            displayBox.autosize();
            displayBox.getChildren().add(WindowUtil.createNewPane(continent));
        }
    }

    /**
     * Method which helps to allocate country to the player.
     */
    private void allocateCountryToPlayerInGamePlay() {
        setChanged();
        notifyObservers("Assigning countries to all players\n");
        startUpPhase.assignCountryToPlayer(map, gamePlayerList, terminalWindow);
    }

    /**
     * The Game goes in the round robin fashion and loads a player
     * for the next turn.
     */
    private void loadCurrentPlayer() {
        if (!playerIterator.hasNext()) {
            playerIterator = gamePlayerList.iterator();
        }
        Player newPlayer = playerIterator.next();
        if (newPlayer.equals(playerPlaying)) {
            if (playerIterator.hasNext()) {
                newPlayer = playerIterator.next();
            }
        }
        playerPlaying = newPlayer;
        Player.setPlayerPlaying(playerPlaying);
        playerPlaying.setCountryWon(0);
        playerPlaying.addObserver(this);
        setChanged();
        notifyObservers(playerPlaying.getName() + "'s turn started.\n");

        selectedCountryList.getItems().clear();
        adjacentCountryList.getItems().clear();
        loadMapData();
        for (Country territory : playerPlaying.getPlayerCountries()) {
            selectedCountryList.getItems().add(territory);
        }
        playerChosen.setText(playerPlaying.getName() + ":- " + playerPlaying.getArmyCount() + " armies left.\n");
    }

    /**
     * Method which helps to calculate the reinforcement armies.
     */
    public void calculateReinforcementArmies() {
        if (this.playerPlaying != null) {
            playerPlaying = playerPlaying.noOfReinforcementArmies(playerPlaying);
            playerChosen.setText(playerPlaying.getName() + ":- " + playerPlaying.getArmyCount() + " armies left.");
        } else {
            setChanged();
            notifyObservers("Wait, no player is assigned the position of current player\n");
        }
    }

    /**
     * Init card Button to open the card window
     *
     * @param event Open card Window Button
     */
    @FXML
    public void initCardWindow(ActionEvent event) {
        CardView.openCardWindow(playerPlaying, card);
    }

    /**
     * Method to initialize reinforcement phase after each player's turn.
     */
    private void initializeReinforcement() {
        loadCurrentPlayer();

        phaseView.setText("Phase: Reinforcement");
        WindowUtil.disableButtonControl(placeArmy, fortify, attack);
        WindowUtil.enableButtonControl(reinforcement, cards);
        reinforcement.requestFocus();
        setChanged();
        notifyObservers("\nReinforcement phase started\n");
        calculateReinforcementArmies();
    }

    /**
     * Method to perform the attack action event after
     * selection of defending country.
     */
    private void performAttack() {
        adjacentCountryList.setOnMouseClicked(e -> attack());
    }

    /**
     * Method to initialize attack phase in the game.
     */
    private void initializeAttack() {
        setChanged();
        notifyObservers("\n Attack phase started.\n");

        if (playerPlaying.playerCanAttack(selectedCountryList, terminalWindow)) {
            phaseView.setText("Phase: Attack");

            WindowUtil.disableButtonControl(reinforcement, placeArmy);
            WindowUtil.enableButtonControl(attack);

            attack.requestFocus();
            performAttack();
        }
    }

    /**
     * Method to initialize fortification in the game.
     */
    private void initializeFortification() {

        WindowUtil.disableButtonControl(reinforcement, attack, placeArmy);
        WindowUtil.enableButtonControl(fortify);
        phaseView.setText("Phase: Fortification");
        fortify.requestFocus();
        setChanged();
        notifyObservers("\nFortification phase started.\n");

    }

    /**
     * Method to generate the graph to populate the World Domination Window.
     */
    private void generateBarGraph() {
        HashMap<Player, Double> playerTerPercent = worldDomination.generateWorldDominationData(map);
        ObservableList<XYChart.Series<String, Double>> answer = FXCollections.observableArrayList();
        ArrayList<XYChart.Series<String, Double>> chartData = new ArrayList<>();
        for (Entry<Player, Double> entry : playerTerPercent.entrySet()) {
            XYChart.Series<String, Double> aSeries = new XYChart.Series<>();
            aSeries.setName(entry.getKey().getName());
            aSeries.getData().add(new XYChart.Data(entry.getKey().getName(), entry.getValue()));
            chartData.add(aSeries);
        }
        answer.addAll(chartData);
        dominationBarChart.setData(answer);
    }

    /**
     * Method to check if any player has lost the game.
     */
    private void isAnyPlayerLost() {
        Player playerLost = playerPlaying.checkPlayerLost(gamePlayerList);
        if (playerLost != null) {
            System.out.println("Player Lost=" + playerLost.getName());
            gamePlayerList.remove(playerLost);
            playerIterator = gamePlayerList.iterator();
            WindowUtil.popUpWindow(playerLost.getName() + " Lost", "Player Lost popup", "Player: " + playerLost.getName() + " lost the game");
            setChanged();
            notifyObservers(playerLost.getName() + " lost the game and hence all the countries.\n\n");
        }
    }

    /**
     * Method to End the Game/
     */
    private void endGame() {
        WindowUtil.disableButtonControl(selectedCountryList, adjacentCountryList, reinforcement, attack, fortify, cards, endTurn);
        phaseView.setText("GAME OVER");
        playerChosen.setText(playerPlaying.getName().toUpperCase() + " WON.");
        setChanged();
        notifyObservers("\n " + playerPlaying.getName().toUpperCase() + " WON.\n\n");
    }

    /**
     * Method to check if any player won the game.
     *
     * @return boolean player status
     */
    private boolean isAnyPlayerWon() {
        boolean playerWon = false;
        if (gamePlayerList.size() == 1) {
            WindowUtil.popUpWindow("Player: " + gamePlayerList.get(0).getName(), "Winning popup", "Game complete.");
            playerWon = true;
            endGame();
        }

        return playerWon;
    }

    /**
     * Method to reset the window after each phase in the game
     */
    private void resetWindow() {
        isAnyPlayerLost();
        selectedCountryList.getItems().clear();
        adjacentCountryList.getItems().clear();
        for (Country country : playerPlaying.getPlayerCountries()) {
            selectedCountryList.getItems().add(country);
        }
        loadMapData();
        generateBarGraph();
        playerChosen.setText(playerPlaying.getName() + ": " + playerPlaying.getArmyCount() + " armies left.\n");
        if (!isAnyPlayerWon()) {
            playerPlaying.playerCanAttack(selectedCountryList, terminalWindow);
        }
    }

    /**
     * Method to initialize placeArmy view.
     */
    private void initializePlaceArmy() {
        loadMapData();
        selectedCountryList.refresh();
        loadCurrentPlayer();
    }

    /**
     * Method to check if fortification phase is valid.
     */
    private void isValidFortificationPhase() {
        playerPlaying.isFortificationPhaseValid(map, playerPlaying);
    }

    /**
     * Method to update the player having few or no armies to fortify through the terminal window
     */
    private void noFortificationPhase() {
        setChanged();
        notifyObservers("Fortification phase started\n");
        setChanged();
        notifyObservers(playerPlaying.getName() + " does not have armies to fortify.\n");
        setChanged();
        notifyObservers("Fortification phase ended\n");
        initializeReinforcement();
    }

    /**
     * Getter Method to get the number of players selected in the initial window.
     *
     * @return number of players playing
     */
    public int getNumberOfPlayersSelected() {
        return numberOfPlayersSelected;
    }

    /**
     * Setter Method to set the number of Players.
     *
     * @param numberOfPlayersSelected number of players selected
     */
    public void setNumberOfPlayersSelected(int numberOfPlayersSelected) {
        this.numberOfPlayersSelected = numberOfPlayersSelected;
    }

    /**
     * Method to exchange the cards to get that number of armies.
     *
     * @param exch Object
     */
    public void exchangeCards(Card exch) {
        List<Card> tradedCards = exch.getCardsToExchange();
        setNumberOfCardSetExchanged(getNumberOfCardSetExchanged() + 1);
        playerPlaying.exchangeCards(tradedCards, getNumberOfCardSetExchanged(), terminalWindow);
        playerPlaying.getCardList().removeAll(tradedCards);
        cardStack.addAll(tradedCards);
        Collections.shuffle(cardStack);
        selectedCountryList.refresh();
        adjacentCountryList.refresh();
        loadMapData();
        generateBarGraph();
        playerChosen.setText(playerPlaying.getName() + ":- " + playerPlaying.getArmyCount() + " armies left.\n");
    }

    /**
     * This method is called whenever the observed object is changed and
     * it also notifies all the observers of the change.
     *
     * @param o   Object
     * @param arg Object
     */
    public void update(Observable o, Object arg) {

        String view = (String) arg;

        if (view.equals("Attack")) {
            initializeAttack();
        }
        if (view.equals("FirstAttack")) {
            loadCurrentPlayer();
            initializeReinforcement();
        }
        if (view.equals("Reinforcement")) {
            initializeReinforcement();
            CardView.openCardWindow(playerPlaying, card);
        }
        if (view.equals("Fortification")) {
            initializeFortification();
        }
        if (view.equals("placeArmyOnCountry")) {
            initializePlaceArmy();
        }
        if (view.equals("WorldDomination")) {
            generateBarGraph();
        }
        if (view.equals("checkIfFortificationPhaseValid")) {
            isValidFortificationPhase();
        }
        if (view.equals("noFortificationMove")) {
            noFortificationPhase();
            CardView.openCardWindow(playerPlaying, card);
        }
        if (view.equals("rollDiceComplete")) {
            resetWindow();
        }
        if (view.equals("cardsExchange")) {
            Card cardObject = (Card) o;
            exchangeCards(cardObject);
        }
    }

    /**
     * Getter to get the number of card sets exchanged.
     *
     * @return number of card sets
     */
    public int getNumberOfCardSetExchanged() {
        return numberOfCardSetExchanged;
    }

    /**
     * Setter method to set the number of card set exchanged.
     *
     * @param numberOfCardSetExchanged numberOfCardSetExchanged
     */
    public void setNumberOfCardSetExchanged(int numberOfCardSetExchanged) {
        this.numberOfCardSetExchanged = numberOfCardSetExchanged;
    }

    @FXML
    private void saveGame(ActionEvent event){

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Game");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Game file extensions(*.save or *.S)","*.SAVE"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if(selectedFile != null){
            saveFile(this,selectedFile);
        }
    }

    private void saveFile(GamePlayController gamePlayController,File file){

        SaveData data = new SaveData();

        data.mapIO = map;
        data.startUpPhase=startUpPhase;
        data.playingPlayer=playerPlaying;
        //playergamephase
        data.card=card;
        data.cardStack=cardStack;
        data.gamePlayerList=gamePlayerList;

        data.terminalWindow=terminalWindow;
        data.phaseView=phaseView.getText();
        data.playerWorldDomination=worldDomination;

        data.numberOfCardsExchanged=numberOfCardSetExchanged;
        data.attackCount=5;
        data.playerChosen=playerChosen;

        try{
            ResourceManager.save(data,"game.save");
        }catch(Exception e){
            System.out.println("Couldn't save: " + e.getMessage());
        }
    }

    public GamePlayController loadFile(){
        isGameSaved = true;
        GamePlayController controller = new GamePlayController();
        try{
            SaveData data = (SaveData) ResourceManager.load("game.save");
            controller.map=data.mapIO;
            controller.startUpPhase=data.startUpPhase;
            controller.playerPlaying=data.playingPlayer;
            controller.card=data.card;
            controller.cardStack=data.cardStack;
            controller.gamePlayerList=data.gamePlayerList;
            controller.terminalWindow=data.terminalWindow;
            controller.phaseViewString=data.phaseView;
            controller.worldDomination=data.playerWorldDomination;
            controller.numberOfCardSetExchanged=data.numberOfCardsExchanged;
            controller.playerChosen=data.playerChosen;
            //attackcount
        }catch (Exception e){
            System.out.println("Couldn't load save data: "+e.getMessage());
        }
        return controller;
    }
}