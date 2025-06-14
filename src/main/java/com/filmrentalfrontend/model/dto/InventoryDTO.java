package com.filmrentalfrontend.model.dto;

public class InventoryDTO {
    private Long inventoryId;
    private Long filmId;
    private StoreDTO store;

    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
    public Long getFilmId() { return filmId; }
    public void setFilmId(Long filmId) { this.filmId = filmId; }
    public StoreDTO getStore() { return store; }
    public void setStore(StoreDTO store) { this.store = store; }

    public static class StoreDTO {
        private Integer storeId;
        public Integer getStoreId() { return storeId; }
        public void setStoreId(Integer storeId) { this.storeId = storeId; }
    }
}