package packetproxy.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import packetproxy.model.*;

import java.util.List;
import java.util.Collections;
import packetproxy.model.Filter;
import packetproxy.model.Filters;

public class FilterIO{

    private static class DaoHub {
        @SerializedName(value="filters") List<Filter> filterList;
    }

    public FilterIO() {
        
    }

    public String getOptions() throws Exception {
		DaoHub daoHub = new DaoHub();

		daoHub.filterList = Filters.getInstance().queryAll();
		Collections.reverse(daoHub.filterList);

		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		String json = gson.toJson(daoHub);
		
		return json;
    }

	public void setOptions(String json){
		try{
			DaoHub daoHub = new Gson().fromJson(json, DaoHub.class);
			
			Database.getInstance().dropFilters();

			for (Filter filter : daoHub.filterList) {
				Filter f = new Filter(filter.getName(),filter.getFilter());
				Filters.getInstance().create(f);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
