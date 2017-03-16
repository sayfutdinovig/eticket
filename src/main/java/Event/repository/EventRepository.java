package Event.repository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class EventRepository implements LimitRepository
{

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public List<Limit> getLimits(List<Integer> outcomesId)
    {
        if (outcomesId == null || outcomesId.isEmpty())
            return null;

        RowMapper<Limit> rm = (rs, rowNum) ->
        {
            Limit outcomeLimit = new Limit();
            outcomeLimit.setId(rs.getInt("id"));
            outcomeLimit.setLimit(rs.getInt("lim"));
            outcomeLimit.setLimitTime(rs.getInt("limit_time"));
            return outcomeLimit;
        };

        String SQL_SELECT_OUTCOME_LIMITS = "";
        return  jdbcTemplate.query(
                PunterUtil.addSQLParametrs(outcomesId.size(), SQL_SELECT_OUTCOME_LIMITS), rm, outcomesId.toArray()
        );
    }


    @Override
    public void updateLimit(Limit limit)
    {
        String SQL_CANCEL_BET = "";
        jdbcTemplate.update(SQL_CANCEL_BET,limit.getLimit(),limit.getLimitTime(),limit.getId());
    }



}

