package org.ethereum.sharding.processing.consensus;

import org.ethereum.sharding.domain.Beacon;
import org.ethereum.sharding.processing.db.ValidatorSet;
import org.ethereum.sharding.processing.state.BeaconState;
import org.ethereum.sharding.processing.state.CrystallizedState;
import org.ethereum.sharding.processing.state.Dynasty;
import org.ethereum.sharding.processing.state.Finality;
import org.ethereum.sharding.service.ValidatorRepository;

import static org.ethereum.sharding.processing.consensus.BeaconConstants.CYCLE_LENGTH;

/**
 * @author Mikhail Kalinin
 * @since 12.09.2018
 */
public class BeaconStateTransition implements StateTransition<BeaconState> {

    StateTransition<ValidatorSet> validatorSetTransition;
    StateTransition<Dynasty> dynastyTransition;
    StateTransition<Finality> finalityTransition;

    public BeaconStateTransition(ValidatorRepository validatorRepository) {
        this.validatorSetTransition = new ValidatorSetTransition(validatorRepository);
        this.dynastyTransition = new DynastyTransition();
        this.finalityTransition = new FinalityTransition();
    }

    public BeaconStateTransition(StateTransition<ValidatorSet> validatorSetTransition,
                                 StateTransition<Dynasty> dynastyTransition,
                                 StateTransition<Finality> finalityTransition) {
        this.validatorSetTransition = validatorSetTransition;
        this.dynastyTransition = dynastyTransition;
        this.finalityTransition = finalityTransition;
    }

    @Override
    public BeaconState applyBlock(Beacon block, BeaconState to) {

        CrystallizedState crystallized = to.getCrystallizedState();

        if (block.getSlotNumber() - crystallized.getLastStateRecalc() >= CYCLE_LENGTH) {
            Finality finality = finalityTransition.applyBlock(block, crystallized.getFinality());
            ValidatorSet validatorSet = validatorSetTransition.applyBlock(block, crystallized.getDynasty().getValidatorSet());
            Dynasty dynasty = crystallized.getDynasty().withValidatorSet(validatorSet);
            dynasty = dynastyTransition.applyBlock(block, dynasty);

            crystallized = crystallized
                    .withDynasty(dynasty)
                    .withLastStateRecalcIncrement(CYCLE_LENGTH)
                    .withFinality(finality);
        }

        return new BeaconState(crystallized);
    }
}